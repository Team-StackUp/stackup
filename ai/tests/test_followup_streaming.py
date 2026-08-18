import pytest
from langchain_core.prompts import ChatPromptTemplate
from ai_server.chain.followup_generation_chain import StreamingFollowupGenerator


class _FakeStreamLLM:
    """astream 시 토큰 조각을 순차로 내는 가짜 모델."""

    def __init__(self, pieces):
        self._pieces = pieces

    async def astream(self, _input, **_kw):
        from langchain_core.messages import AIMessageChunk

        for p in self._pieces:
            yield AIMessageChunk(content=p)


@pytest.mark.asyncio
async def test_stream_emits_question_tokens_and_final_result():
    pieces = [
        "<intent>NORMAL</intent><que",
        "stion>그 ",
        "설계에서 ",
        "트레이드오프는?",
        "</question><meta>",
        '{"specificity":2,"logic":3,"structure":"NONE","correctness":null}</meta>',
    ]
    prompt = ChatPromptTemplate.from_messages([("human", "{answer_text}")])
    gen = StreamingFollowupGenerator(prompt=prompt, llm=_FakeStreamLLM(pieces))

    seen = []
    result = await gen.stream(
        on_question_token=lambda piece: seen.append(piece),
        job_category="BACKEND",
        mode="TECHNICAL",
        previous_question="q",
        answer_text="a",
        context="(none)",
        parent_category="X",
        expected_signal="(none)",
        history="(none)",
    )
    joined = "".join(seen)
    assert "그 설계에서 트레이드오프는?" in joined
    assert "<intent>" not in joined and "<meta>" not in joined  # 질문만 흘렀다
    assert result.answer_intent == "NORMAL"
    assert result.followup_question == "그 설계에서 트레이드오프는?"


@pytest.mark.asyncio
async def test_stream_emits_full_question_with_lt_operator():
    # 부등호 '<' 가 든 질문이 스트리밍 중 '<' 에서 잘리지 않고 끝까지 흘러야 한다.
    pieces = [
        "<intent>NORMAL</intent><question>리스트에서 a ",
        "< b 를 ",
        "만족하는 쌍을 세는 법은?",
        "</question><meta>{}</meta>",
    ]
    prompt = ChatPromptTemplate.from_messages([("human", "{answer_text}")])
    gen = StreamingFollowupGenerator(prompt=prompt, llm=_FakeStreamLLM(pieces))

    seen = []
    result = await gen.stream(
        on_question_token=lambda piece: seen.append(piece),
        job_category="BACKEND",
        mode="TECHNICAL",
        previous_question="q",
        answer_text="a",
        context="(none)",
        parent_category="X",
        expected_signal="(none)",
        history="(none)",
    )
    joined = "".join(seen)
    assert joined == "리스트에서 a < b 를 만족하는 쌍을 세는 법은?"
    assert result.followup_question == "리스트에서 a < b 를 만족하는 쌍을 세는 법은?"


@pytest.mark.asyncio
async def test_stream_dont_know_emits_no_question_tokens():
    pieces = [
        "<intent>DONT_KNOW</intent><question>모르면 이렇게</question><meta>{}</meta>"
    ]
    prompt = ChatPromptTemplate.from_messages([("human", "{answer_text}")])
    gen = StreamingFollowupGenerator(prompt=prompt, llm=_FakeStreamLLM(pieces))
    seen = []
    result = await gen.stream(
        on_question_token=lambda piece: seen.append(piece),
        job_category="BACKEND",
        mode="TECHNICAL",
        previous_question="q",
        answer_text="a",
        context="(none)",
        parent_category="X",
        expected_signal="(none)",
        history="(none)",
    )
    assert seen == []  # DONT_KNOW 는 질문 델타 0개
    assert result.answer_intent == "DONT_KNOW"
