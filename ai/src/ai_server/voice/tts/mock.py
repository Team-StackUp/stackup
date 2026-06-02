from __future__ import annotations

from ai_server.voice.tts.base import TtsProvider, TtsResult


class MockTtsProvider(TtsProvider):
    model_name = "mock-tts"

    async def synthesize(self, text: str, *, voice: str) -> TtsResult:
        # 텍스트 길이에 비례한 가짜 오디오 바이트 + 추정 길이(분당 ~300자 가정).
        payload = ("MOCKMP3:" + text).encode("utf-8")
        duration = max(0.5, round(len(text) / 5.0, 2))
        return TtsResult(audio_bytes=payload, duration_sec=duration, content_type="audio/mpeg")
