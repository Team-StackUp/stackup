import pytest

from ai_server.voice.stt.mock_live import MockLiveSttProvider


@pytest.mark.asyncio
async def test_mock_live_emits_partials_then_final():
    provider = MockLiveSttProvider(
        script=["안녕", "안녕하세요", "안녕하세요 반갑습니다"]
    )
    session = provider.open_session(content_type="audio/webm", language="ko")
    await session.start()
    # 오디오 청크 3개 투입
    for i in range(3):
        await session.push(b"audiochunk")
    await session.finish()

    events = []
    async for ev in session.events():
        events.append(ev)

    assert any(not e.is_final for e in events), "부분 이벤트 존재"
    assert events[-1].speech_final is True
    result = await session.result()
    assert result.text == "안녕하세요 반갑습니다"
    assert result.segments, "메트릭용 segment 존재"
    await session.close()
