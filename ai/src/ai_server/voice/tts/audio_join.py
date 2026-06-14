from __future__ import annotations

import io
import wave

from ai_server.voice.tts.base import TtsResult


def join_audio(parts: list[TtsResult]) -> TtsResult:
    """여러 세그먼트 오디오를 하나로 합친다(재합성 없음).

    - WAV(Gemini/게이트웨이): PCM 프레임을 이어붙여 단일 WAV 로 — 헤더 중복 없는 정상 파일.
    - 그 외(mp3 등): 컨테이너 바이트 단순 연결(best-effort, 대부분 플레이어가 재생).
    """
    if not parts:
        raise ValueError("join_audio: empty parts")
    if len(parts) == 1:
        return parts[0]

    total_dur = _sum_dur(parts)
    if all(_ctype(p) == "audio/wav" for p in parts):
        return TtsResult(
            audio_bytes=_concat_wav([p.audio_bytes for p in parts]),
            duration_sec=total_dur,
            content_type="audio/wav",
        )
    return TtsResult(
        audio_bytes=b"".join(p.audio_bytes for p in parts),
        duration_sec=total_dur,
        content_type=parts[0].content_type,
    )


def _ctype(part: TtsResult) -> str:
    return part.content_type.split(";")[0].strip()


def _sum_dur(parts: list[TtsResult]) -> float | None:
    if any(p.duration_sec is None for p in parts):
        return None
    return round(sum(p.duration_sec or 0.0 for p in parts), 2)


def _concat_wav(wavs: list[bytes]) -> bytes:
    frames: list[bytes] = []
    params = None
    for w in wavs:
        with wave.open(io.BytesIO(w), "rb") as reader:
            if params is None:
                params = reader.getparams()
            frames.append(reader.readframes(reader.getnframes()))
    assert params is not None
    buf = io.BytesIO()
    with wave.open(buf, "wb") as out:
        out.setnchannels(params.nchannels)
        out.setsampwidth(params.sampwidth)
        out.setframerate(params.framerate)
        out.writeframes(b"".join(frames))
    return buf.getvalue()
