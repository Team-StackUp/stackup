"""후보 LLM 을 운영 체인 그대로 태워 원시 결과를 JSONL 로 남긴다.

stackup-ai 컨테이너 안에서 실행 (운영 의존성·네트워크 그대로):
    python run_eval.py --label qwen3-4b --base-url http://ollama:11434/v1 --api-key ollama \
        --model stackup-qwen3-4b-instruct-8k --suites followup,questions,coaching --reps 3 \
        --latency --out /tmp/eval/qwen3-4b.jsonl
게이트웨이 모델은 --base-url/--api-key 생략 (컨테이너 env 의 LLM_BASE_URL/LLM_API_KEY 사용).

측정:
- followup: 운영 StreamingFollowupGenerator.stream (첫 질문 토큰 시점 TTFT 포함)
- questions: 운영 질문 풀 체인 (PydanticOutputParser)
- coaching: 운영 답변 코칭 체인
- --latency: 콜드스타트(ollama 언로드 후 첫 호출), 피드백 fan-out 동시성(15건/동시 5),
  fan-out 도중 꼬리질문 지연
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time
import urllib.request
from typing import Any

from langchain_core.callbacks import AsyncCallbackHandler

sys.path.insert(0, os.path.dirname(__file__))

import importlib as _il  # noqa: E402

_C = _il.import_module(os.environ.get("LLM_EVAL_CASES", "cases"))  # noqa: E402
COACHING_CASES, FOLLOWUP_CASES, QUESTION_CASES = (
    _C.COACHING_CASES,
    _C.FOLLOWUP_CASES,
    _C.QUESTION_CASES,
)

from ai_server.chain.feedback_generation_chain import (  # noqa: E402
    LlmAnswerCoach,
    build_answer_coaching_chain,
)
from ai_server.chain.followup_generation_chain import (  # noqa: E402
    build_streaming_followup_generator,
)
from ai_server.chain.question_generation_chain import (  # noqa: E402
    LlmQuestionGenerator,
    build_question_generation_chain,
)
from ai_server.config.settings import Settings  # noqa: E402


class UsageCapture(AsyncCallbackHandler):
    def __init__(self) -> None:
        self.in_tokens: int | None = None
        self.out_tokens: int | None = None

    async def on_llm_end(self, response, **kwargs: Any) -> None:  # noqa: ANN001
        try:
            gen = response.generations[0][0]
            meta = getattr(getattr(gen, "message", None), "usage_metadata", None)
            if meta:
                self.in_tokens = meta.get("input_tokens")
                self.out_tokens = meta.get("output_tokens")
                return
        except Exception:  # noqa: BLE001
            pass
        usage = (response.llm_output or {}).get("token_usage") or {}
        self.in_tokens = usage.get("prompt_tokens", self.in_tokens)
        self.out_tokens = usage.get("completion_tokens", self.out_tokens)


EXTRA_BODY: dict[str, Any] = {}
# --constrain-json: 질문 풀·코칭 체인에 JSON 스키마 강제(response_format json_schema) 적용
CONSTRAIN_JSON = {"on": False}


def _json_schema_body(model_cls: Any, name: str) -> dict[str, Any]:
    return {
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": name,
                "schema": model_cls.model_json_schema(by_alias=True),
            },
        }
    }


def _apply_body(node: Any, body: dict[str, Any]) -> None:
    from langchain_openai import ChatOpenAI

    if isinstance(node, ChatOpenAI):
        node.extra_body = {**(node.extra_body or {}), **body}
        return
    for attr in ("steps", "first", "middle", "last", "bound"):
        child = getattr(node, attr, None)
        if child is None:
            continue
        for c in child if isinstance(child, (list, tuple)) else [child]:
            _apply_body(c, body)


def _apply_extra_body(node: Any) -> None:
    """체인/LLM 트리 안의 ChatOpenAI 에 extra_body 주입 (예: reasoning_effort=none)."""
    if not EXTRA_BODY:
        return
    from langchain_openai import ChatOpenAI

    if isinstance(node, ChatOpenAI):
        node.extra_body = {**(node.extra_body or {}), **EXTRA_BODY}
        return
    for attr in ("steps", "first", "middle", "last", "bound"):
        child = getattr(node, attr, None)
        if child is None:
            continue
        for c in child if isinstance(child, (list, tuple)) else [child]:
            _apply_extra_body(c)


def make_settings(args: argparse.Namespace) -> Settings:
    over: dict[str, Any] = {
        "llm_pro_timeout_sec": args.timeout,
        "llm_flash_timeout_sec": args.timeout,
        "llm_pro_model": args.model,
        "llm_flash_model": args.model,
    }
    if args.base_url:
        over["llm_base_url"] = args.base_url
    if args.api_key is not None:
        over["llm_api_key"] = args.api_key
    if args.flash_max_tokens:
        over["llm_flash_max_tokens"] = args.flash_max_tokens
    return Settings(**over)


def _emit(fh, rec: dict[str, Any]) -> None:
    fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
    fh.flush()
    status = "ok " if rec["ok"] else "ERR"
    extra = f" ttft={rec.get('ttft_sec')}" if rec.get("ttft_sec") is not None else ""
    print(
        f"  [{status}] {rec['suite']:<9} {rec['case_id']:<28} rep{rec['rep']} "
        f"{rec['latency_sec']:>6}s{extra} out={rec.get('out_tokens')}",
        file=sys.stderr,
    )


async def run_followup(
    settings: Settings, case: dict, rep: int, label: str, tag: str = ""
) -> dict:
    gen = build_streaming_followup_generator(settings)
    gen._llm.stream_usage = True  # noqa: SLF001
    cap = UsageCapture()
    gen._llm.callbacks = [cap]  # noqa: SLF001
    _apply_extra_body(gen._llm)  # noqa: SLF001
    kwargs = {
        k: case[k]
        for k in (
            "job_category",
            "mode",
            "previous_question",
            "answer_text",
            "context",
            "parent_category",
            "expected_signal",
            "history",
        )
    }
    t0 = time.perf_counter()
    first: list[float] = []

    def on_tok(_d: str) -> None:
        if not first:
            first.append(time.perf_counter() - t0)

    rec: dict[str, Any] = {
        "label": label,
        "suite": "followup" + tag,
        "case_id": case["id"],
        "rep": rep,
    }
    try:
        res = await gen.stream(on_question_token=on_tok, **kwargs)
        rec.update(
            ok=True,
            followup_question=res.followup_question,
            answer_intent=res.answer_intent,
            answer_evaluation=(
                res.answer_evaluation.model_dump() if res.answer_evaluation else None
            ),
        )
    except Exception as exc:  # noqa: BLE001
        rec.update(ok=False, error=f"{type(exc).__name__}: {str(exc)[:500]}")
    rec["latency_sec"] = round(time.perf_counter() - t0, 3)
    rec["ttft_sec"] = round(first[0], 3) if first else None
    rec["in_tokens"], rec["out_tokens"] = cap.in_tokens, cap.out_tokens
    return rec


async def run_questions(settings: Settings, case: dict, rep: int, label: str) -> dict:
    cap = UsageCapture()
    base = build_question_generation_chain(settings)
    _apply_extra_body(base)
    if CONSTRAIN_JSON["on"]:
        from ai_server.chain.question_generation_chain import GeneratedQuestionPool

        _apply_body(base, _json_schema_body(GeneratedQuestionPool, "question_pool"))
    chain = base.with_config(callbacks=[cap])
    gen = LlmQuestionGenerator(chain)
    t0 = time.perf_counter()
    rec: dict[str, Any] = {
        "label": label,
        "suite": "questions",
        "case_id": case["id"],
        "rep": rep,
    }
    try:
        pool = await gen.generate(
            job_categories=case["job_categories"],
            mode=case["mode"],
            max_questions=case["max_questions"],
            context=case["context"],
            recent_questions=case.get("recent_questions"),
            self_introduction=case.get("self_introduction"),
            target_company_name=case.get("target_company_name"),
            target_job_description=case.get("target_job_description"),
            focus_areas=case.get("focus_areas"),
        )
        rec.update(ok=True, questions=[q.model_dump() for q in pool.questions])
    except Exception as exc:  # noqa: BLE001
        rec.update(ok=False, error=f"{type(exc).__name__}: {str(exc)[:800]}")
    rec["latency_sec"] = round(time.perf_counter() - t0, 3)
    rec["in_tokens"], rec["out_tokens"] = cap.in_tokens, cap.out_tokens
    return rec


async def run_coaching(
    settings: Settings, case: dict, rep: int, label: str, tag: str = ""
) -> dict:
    cap = UsageCapture()
    base = build_answer_coaching_chain(settings)
    _apply_extra_body(base)
    if CONSTRAIN_JSON["on"]:
        from ai_server.chain.feedback_generation_chain import CoachingResult

        _apply_body(base, _json_schema_body(CoachingResult, "coaching"))
    chain = base.with_config(callbacks=[cap])
    coach = LlmAnswerCoach(chain)
    t0 = time.perf_counter()
    rec: dict[str, Any] = {
        "label": label,
        "suite": "coaching" + tag,
        "case_id": case["id"],
        "rep": rep,
    }
    try:
        res = await coach.coach(
            **{
                k: case[k]
                for k in (
                    "job_category",
                    "mode",
                    "target_role",
                    "question",
                    "expected_signal",
                    "answer",
                    "rag_context",
                )
            }
        )
        rec.update(ok=True, coaching=res.model_dump())
    except Exception as exc:  # noqa: BLE001
        rec.update(ok=False, error=f"{type(exc).__name__}: {str(exc)[:500]}")
    rec["latency_sec"] = round(time.perf_counter() - t0, 3)
    rec["in_tokens"], rec["out_tokens"] = cap.in_tokens, cap.out_tokens
    return rec


def ollama_unload(base_url: str, model: str) -> None:
    root = base_url.rstrip("/").removesuffix("/v1")
    body = json.dumps({"model": model, "keep_alive": 0}).encode()
    req = urllib.request.Request(
        f"{root}/api/generate", data=body, headers={"Content-Type": "application/json"}
    )
    urllib.request.urlopen(req, timeout=60).read()


async def latency_suite(settings: Settings, args: argparse.Namespace, fh) -> None:
    label = args.label
    is_ollama = "11434" in (args.base_url or "")
    # 1) 콜드스타트: 언로드 후 첫 꼬리질문
    if is_ollama:
        ollama_unload(args.base_url, args.model)
        await asyncio.sleep(2)
        for i, case in enumerate(FOLLOWUP_CASES[:2]):
            rec = await run_followup(
                settings, case, i, label, tag=":cold" if i == 0 else ":after-cold"
            )
            _emit(fh, rec)

    # 2) 피드백 fan-out: 코칭 15건을 동시성 5 로 (운영 FEEDBACK_COACHING_CONCURRENCY=5)
    sem = asyncio.Semaphore(5)
    jobs = [COACHING_CASES[i % len(COACHING_CASES)] for i in range(15)]

    async def one(i: int, case: dict) -> dict:
        async with sem:
            return await run_coaching(settings, case, i, label, tag=":fanout15x5")

    t0 = time.perf_counter()

    # 3) fan-out 시작 3초 뒤 다른 사용자의 꼬리질문이 들어온다고 가정
    async def contending_followup() -> dict:
        await asyncio.sleep(3)
        return await run_followup(
            settings, FOLLOWUP_CASES[0], 0, label, tag=":during-fanout"
        )

    results = await asyncio.gather(
        *(one(i, c) for i, c in enumerate(jobs)), contending_followup()
    )
    wall = round(time.perf_counter() - t0, 3)
    for rec in results:
        _emit(fh, rec)
    fh.write(
        json.dumps(
            {"label": label, "suite": "fanout_wall", "wall_sec": wall, "ok": True}
        )
        + "\n"
    )
    print(f"  fan-out 15x5 wall={wall}s", file=sys.stderr)


async def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--base-url", default="")
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--suites", default="followup,questions,coaching")
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--question-reps", type=int, default=2)
    ap.add_argument("--timeout", type=float, default=240.0)
    ap.add_argument("--flash-max-tokens", type=int, default=0)
    ap.add_argument("--latency", action="store_true")
    ap.add_argument(
        "--constrain-json", action="store_true", help="질문 풀·코칭에 JSON 스키마 강제"
    )
    ap.add_argument(
        "--extra-body", default="", help='JSON, 예: \'{"reasoning_effort": "none"}\''
    )
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    settings = make_settings(args)
    CONSTRAIN_JSON["on"] = args.constrain_json
    if args.extra_body:
        EXTRA_BODY.update(json.loads(args.extra_body))
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    suites = [s for s in args.suites.split(",") if s]
    print(
        f"== {args.label} model={args.model} base={settings.llm_base_url}",
        file=sys.stderr,
    )
    with open(args.out, "a", encoding="utf-8") as fh:
        if "followup" in suites:
            # 워밍업 1회 (결과 기록 안 함) — 로드 시간과 품질 측정을 분리
            await run_followup(settings, FOLLOWUP_CASES[0], -1, args.label)
            for rep in range(args.reps):
                for case in FOLLOWUP_CASES:
                    _emit(fh, await run_followup(settings, case, rep, args.label))
        if "coaching" in suites:
            for rep in range(args.reps):
                for case in COACHING_CASES:
                    _emit(fh, await run_coaching(settings, case, rep, args.label))
        if "questions" in suites:
            for rep in range(args.question_reps):
                for case in QUESTION_CASES:
                    _emit(fh, await run_questions(settings, case, rep, args.label))
        if args.latency:
            await latency_suite(settings, args, fh)
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
