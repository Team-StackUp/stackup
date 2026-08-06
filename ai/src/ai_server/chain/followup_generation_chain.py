from __future__ import annotations

import re
from collections.abc import Awaitable, Callable
from typing import Any, Literal, Protocol

from langchain_core.output_parsers import PydanticOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_core.runnables import Runnable
from pydantic import BaseModel, Field

from ai_server.chain.prompts.followup_generation import HUMAN_PROMPT, SYSTEM_PROMPT
from ai_server.config.settings import Settings
from ai_server.core.client import CoreClient
from ai_server.model.messages.followup import AnswerEvaluation
from ai_server.observability.llm_logging_callback import CoreAiLogCallback


class FollowupResult(BaseModel):
    followup_question: str = Field(..., description="한국어 꼬리질문 1개")
    answer_evaluation: AnswerEvaluation | None = None
    # 답변 의도. NORMAL=정상답변, DONT_KNOW=모름/포기, CLARIFICATION=질문 재설명 요청.
    answer_intent: Literal["NORMAL", "DONT_KNOW", "CLARIFICATION"] = "NORMAL"


_INTENT_RE = re.compile(r"<intent>\s*(.*?)\s*</intent>", re.DOTALL)
_QUESTION_RE = re.compile(r"<question>\s*(.*?)\s*</question>", re.DOTALL)
_META_RE = re.compile(r"<meta>\s*(\{.*?\})\s*</meta>", re.DOTALL)
_VALID_INTENTS = {"NORMAL", "DONT_KNOW", "CLARIFICATION"}


def extract_question_span(text: str) -> str:
    """완성 또는 진행 중 텍스트에서 <question> 안쪽만 추출(닫는 태그 없어도 가능)."""
    closed = _QUESTION_RE.search(text)
    if closed:
        return closed.group(1).strip()
    open_idx = text.find("<question>")
    if open_idx == -1:
        return ""
    tail = text[open_idx + len("<question>") :]
    tail = tail.split("<meta>", 1)[0]
    # 닫는/메타 태그가 스트림 청크 경계로 잘려 들어온 경우(예: "질문?</quest")만 그
    # partial 을 제거한다. 질문 본문의 부등호·제네릭('a < b', 'List<T>')까지 잘라내지
    # 않도록, 마지막 '<' 꼬리가 태그 시작과 일치할 때만 자른다.
    lt = tail.rfind("<")
    if lt != -1 and ">" not in tail[lt:]:
        partial = tail[lt:]
        if "</question>".startswith(partial) or "<meta>".startswith(partial):
            tail = tail[:lt]
    return tail.strip()


def parse_followup_result(text: str) -> FollowupResult:
    """종료된 누적 텍스트를 FollowupResult 로. 태그 누락 시 전체를 질문으로 폴백."""
    intent_m = _INTENT_RE.search(text)
    intent = intent_m.group(1).strip().upper() if intent_m else "NORMAL"
    if intent not in _VALID_INTENTS:
        intent = "NORMAL"

    question = extract_question_span(text)
    if not question:
        question = text.strip()

    evaluation = None
    meta_m = _META_RE.search(text)
    if meta_m:
        try:
            evaluation = AnswerEvaluation.model_validate_json(meta_m.group(1))
        except Exception:
            evaluation = None

    return FollowupResult(
        followup_question=question,
        answer_evaluation=evaluation,
        answer_intent=intent,
    )


class StreamingFollowupGenerator:
    """단일 LLM 콜을 astream 으로 흘리며 <question> 토큰만 콜백으로 내보낸다.

    intent 가 DONT_KNOW 면 질문 델타를 보내지 않는다(Core 가 폐기하므로).
    종료 후 누적 텍스트를 parse_followup_result 로 검증해 반환.
    """

    def __init__(self, *, prompt: ChatPromptTemplate, llm: Any) -> None:
        self._prompt = prompt
        self._llm = llm

    async def stream(
        self,
        *,
        on_question_token: Callable[[str], Awaitable[None] | None],
        job_category: str,
        mode: str,
        previous_question: str,
        answer_text: str,
        context: str,
        parent_category: str,
        expected_signal: str,
        history: str,
    ) -> FollowupResult:
        all_vars = {
            "job_category": job_category,
            "mode": mode,
            "previous_question": previous_question,
            "answer_text": answer_text,
            "context": context,
            "parent_category": parent_category,
            "expected_signal": expected_signal,
            "history": history,
        }
        # 프롬프트가 선언한 변수만 전달(템플릿마다 변수 집합이 다를 수 있음).
        wanted = set(self._prompt.input_variables)
        prompt_vars = {k: v for k, v in all_vars.items() if k in wanted}
        messages = self._prompt.format_messages(**prompt_vars)

        acc = ""
        emitted_q = ""
        async for chunk in self._llm.astream(messages):
            piece = getattr(chunk, "content", "") or ""
            if not piece:
                continue
            acc += piece
            intent_known = _INTENT_RE.search(acc)
            if not intent_known:
                continue
            if intent_known.group(1).strip().upper() == "DONT_KNOW":
                continue
            q_now = extract_question_span(acc)
            if len(q_now) > len(emitted_q):
                delta = q_now[len(emitted_q) :]
                emitted_q = q_now
                res = on_question_token(delta)
                if res is not None and hasattr(res, "__await__"):
                    await res

        return parse_followup_result(acc)


class FollowupGenerator(Protocol):
    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        previous_question: str,
        answer_text: str,
        context: str = "(none)",
        parent_category: str = "UNKNOWN",
        expected_signal: str = "(none)",
        history: str = "(none)",
    ) -> FollowupResult: ...


class LlmFollowupGenerator:
    def __init__(self, chain: Runnable) -> None:
        self._chain = chain

    async def generate(
        self,
        *,
        job_category: str,
        mode: str,
        previous_question: str,
        answer_text: str,
        context: str = "(none)",
        parent_category: str = "UNKNOWN",
        expected_signal: str = "(none)",
        history: str = "(none)",
    ) -> FollowupResult:
        result = await self._chain.ainvoke(
            {
                "job_category": job_category,
                "mode": mode,
                "previous_question": previous_question,
                "answer_text": answer_text,
                "context": context,
                "parent_category": parent_category,
                "expected_signal": expected_signal,
                "history": history,
            }
        )
        if not isinstance(result, FollowupResult):
            raise TypeError(
                f"chain returned {type(result).__name__}, expected FollowupResult"
            )
        return result


def build_followup_generation_chain(
    settings: Settings, core_client: CoreClient | None = None
) -> Runnable:
    from langchain_openai import ChatOpenAI

    parser = PydanticOutputParser(pydantic_object=FollowupResult)
    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", SYSTEM_PROMPT),
            ("human", HUMAN_PROMPT),
        ]
    ).partial(format_instructions=parser.get_format_instructions())

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.followup",
                default_model=settings.llm_flash_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=settings.llm_flash_temperature,
        timeout=settings.llm_flash_timeout_sec,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        max_tokens=settings.llm_flash_max_tokens,
        callbacks=callbacks,
    )
    return prompt | llm | parser


def build_streaming_followup_generator(
    settings: Settings, core_client: CoreClient | None = None
) -> StreamingFollowupGenerator:
    """스트리밍 꼬리질문 생성기 빌더.

    format_instructions 없는 프롬프트 + Flash LLM 을 조합해
    StreamingFollowupGenerator 를 반환한다.
    """
    from langchain_openai import ChatOpenAI

    prompt = ChatPromptTemplate.from_messages(
        [
            ("system", SYSTEM_PROMPT),
            ("human", HUMAN_PROMPT),
        ]
    )

    callbacks = []
    if core_client is not None:
        callbacks.append(
            CoreAiLogCallback(
                core_client=core_client,
                request_type="generate.followup.stream",
                default_model=settings.llm_flash_model,
            )
        )

    llm = ChatOpenAI(
        model=settings.llm_flash_model,
        temperature=settings.llm_flash_temperature,
        timeout=settings.llm_flash_timeout_sec,
        api_key=settings.llm_api_key or None,
        base_url=settings.llm_base_url,
        max_tokens=settings.llm_flash_max_tokens,
        callbacks=callbacks,
    )
    return StreamingFollowupGenerator(prompt=prompt, llm=llm)
