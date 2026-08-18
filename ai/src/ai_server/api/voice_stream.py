from __future__ import annotations

import asyncio

import structlog
from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect

from ai_server.config.settings import Settings
from ai_server.messaging.publisher import CallbackPublisher
from ai_server.model.envelope import MessageContext
from ai_server.model.messages.voice import VoiceCallbackPayload
from ai_server.voice.analysis.metrics import analyze
from ai_server.voice.stt.live import LiveSttProvider

log = structlog.get_logger(__name__)

router = APIRouter()


def _provider(ws: WebSocket) -> LiveSttProvider:
    return ws.app.state.live_stt_provider


def _publisher(ws: WebSocket) -> CallbackPublisher:
    return ws.app.state.callback_publisher


def _settings(ws: WebSocket) -> Settings:
    return ws.app.state.settings


@router.websocket("/internal/voice/stream")
async def voice_stream(
    ws: WebSocket,
    session_id: int = Query(..., alias="sessionId"),
    message_id: int = Query(..., alias="messageId"),
    content_type: str = Query("audio/webm", alias="contentType"),
    api_key: str | None = Query(None, alias="apiKey"),
) -> None:
    settings = _settings(ws)
    # 내부 인증: RealTime 이 전달한 키 검증(쿼리 또는 헤더).
    expected = settings.core_internal_api_key
    provided = api_key or ws.headers.get("x-internal-api-key")
    if expected and provided != expected:
        await ws.close(code=4401)
        return

    await ws.accept()
    provider = _provider(ws)
    publisher = _publisher(ws)
    session = provider.open_session(content_type=content_type, language=None)
    await session.start()

    async def pump_transcripts() -> None:
        async for ev in session.events():
            payload = {
                "type": "transcript.final" if ev.is_final else "transcript.partial",
                "text": ev.text,
            }
            if ev.is_final:
                payload["messageId"] = message_id
            try:
                await ws.send_json(payload)
            except Exception:  # noqa: BLE001
                return

    pump = asyncio.create_task(pump_transcripts())
    stopped = False
    try:
        while True:
            msg = await ws.receive()
            if msg["type"] == "websocket.disconnect":
                break
            if msg.get("bytes") is not None:
                await session.push(msg["bytes"])
            elif msg.get("text") is not None and '"stop"' in msg["text"]:
                stopped = True
                break
    except WebSocketDisconnect:
        pass
    finally:
        await session.finish()
        # 남은 최종 이벤트 flush. Deepgram 등 upstream 이 CloseStream 에 무응답이면
        # pump(events 생성기 소진)이 영구 대기할 수 있으므로 timeout 으로 상한.
        try:
            await asyncio.wait_for(pump, timeout=10.0)
        except asyncio.TimeoutError:
            pump.cancel()
            log.warn(
                "voice_stream.pump.timeout",
                session_id=session_id,
                message_id=message_id,
            )
        result = await session.result()
        await session.close()

        if result.text.strip():
            metrics = analyze(result, filler_pattern=settings.voice_filler_pattern)
            cb = VoiceCallbackPayload(
                session_id=session_id,
                interview_message_id=message_id,
                transcript=result.text,
                speaking_rate_wpm=metrics.speaking_rate_wpm,
                silence_duration_sec=metrics.silence_duration_sec,
                filler_word_counts=metrics.filler_word_counts,
                pronunciation_accuracy=metrics.pronunciation_accuracy,
                error_code=None,
            )
        else:
            cb = VoiceCallbackPayload(
                session_id=session_id,
                interview_message_id=message_id,
                transcript=None,
                filler_word_counts={},
                error_code="TRANSCRIPTION_EMPTY",
            )
        try:
            await publisher.publish(
                routing_key=settings.ai_callback_routing_voice,
                message_type="callback.voice",
                payload=cb,
                trace_id=f"voice-stream-{session_id}-{message_id}",
                correlation_id=f"voice-stream-{message_id}",
                context=MessageContext(session_id=session_id),
            )
            log.info(
                "voice_stream.callback.published",
                session_id=session_id,
                interview_message_id=message_id,
                stopped=stopped,
                chars=len(result.text or ""),
            )
        except Exception as exc:  # noqa: BLE001
            log.error(
                "voice_stream.callback.failed", error=str(exc), session_id=session_id
            )
        try:
            await ws.close()
        except Exception:  # noqa: BLE001
            pass
