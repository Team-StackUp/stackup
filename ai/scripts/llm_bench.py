"""LLM 후보(게이트웨이 vs 로컬 Ollama 등) 를 실제 체인으로 비교하는 벤치마크.

실제 프롬프트·파서(question_generation / followup_generation) 를 그대로 태우므로
"이 모델이 우리 JSON 스키마를 지키는가, 한국어 품질은 어떤가, 지연은 얼마인가" 를
운영 코드와 같은 조건에서 본다.

사용 예 (stackup-ai 컨테이너 안에서):
    python scripts/llm_bench.py --base-url http://ollama:11434/v1 --api-key ollama \
        --model qwen3:4b --runs 2 --out /tmp/bench-qwen3-4b.json
    python scripts/llm_bench.py --model gemini-3.5-flash-lite --runs 2   # 게이트웨이 기본값

--extra-body 로 공급자 전용 옵션을 넘길 수 있다 (예: Ollama qwen3 thinking 끄기
  '{"think": false}').
"""

from __future__ import annotations

import argparse
import asyncio
import json
import statistics
import sys
import time
from typing import Any

from ai_server.chain.followup_generation_chain import (
    build_streaming_followup_generator,
)
from ai_server.chain.question_generation_chain import (
    LlmQuestionGenerator,
    build_question_generation_chain,
)
from ai_server.config.settings import Settings

SAMPLE_RESUME_MD = """# 이력서 — 김도현 (백엔드 개발자, 3년차)

## 요약
- Spring Boot / Kotlin 기반 커머스 주문·정산 도메인 3년
- MSA 전환 프로젝트에서 주문 서비스 분리 및 Kafka 이벤트 파이프라인 설계
- 장애 대응: 결제 승인 지연으로 인한 중복 주문 이슈를 멱등 키 + Outbox 패턴으로 해결

## 경력
### (주)마켓온 — 백엔드 개발자 (2023.03 ~ 현재)
- 주문/결제 도메인 담당. 일 평균 주문 12만 건 처리
- 모놀리식 → MSA 전환: 주문 서비스를 별도 서비스로 분리, DB 분리(PostgreSQL) 및
  Kafka 기반 이벤트 발행/구독 구조 도입. 배포 단위 축소로 배포 주기 2주 → 2일
- 정산 배치 성능 개선: 5시간 → 40분 (QueryDSL 튜닝, 청크 단위 처리, 인덱스 재설계)
- 결제 승인 콜백 지연 시 중복 주문 발생 문제: 멱등 키 테이블 + Transactional Outbox 로
  해결, 중복 주문 0건 달성

### 스타트업 인턴 — 서버 개발 (2022.07 ~ 2022.12)
- Node.js/Express 기반 사내 예약 시스템 API 개발, Jest 테스트 커버리지 70% 달성

## 프로젝트
### 실시간 재고 동기화 (2024)
- Redis 기반 재고 캐시 + DB write-behind, 동시성 제어(낙관적 락 → 분산 락 전환)
- 재고 불일치 건수 월 200건 → 3건

## 기술 스택
Kotlin, Java 17, Spring Boot 3, JPA/QueryDSL, PostgreSQL, Kafka, Redis, Docker, GitHub Actions
"""

SAMPLE_FOLLOWUP = {
    "job_category": "BACKEND",
    "mode": "TECHNICAL",
    "previous_question": (
        "결제 승인 콜백 지연으로 중복 주문이 발생했던 문제를 멱등 키와 Outbox 패턴으로 "
        "해결하셨다고 했는데, 두 가지를 함께 쓴 이유와 각각이 어떤 실패 케이스를 막아주는지 설명해 주세요."
    ),
    "answer_text": (
        "네, 먼저 멱등 키는 같은 결제 승인 콜백이 두 번 들어와도 주문이 한 번만 생성되게 하려고 "
        "썼습니다. PG사에서 타임아웃 후 재전송을 하다 보니 같은 승인 건이 두 번 오는 경우가 있었고, "
        "결제 키를 유니크 제약으로 걸어서 두 번째 요청은 기존 주문을 그대로 돌려주도록 했습니다. "
        "Outbox 는 주문 저장이랑 Kafka 이벤트 발행이 하나의 트랜잭션이 아니어서, 주문은 저장됐는데 "
        "이벤트가 안 나가는 경우가 있었어요. 그래서 이벤트를 같은 DB 트랜잭션 안에서 outbox 테이블에 "
        "먼저 쓰고, 별도 릴레이가 폴링해서 발행하도록 바꿨습니다."
    ),
    "context": "(none)",
    "parent_category": "PROJECT",
    "expected_signal": "멱등성과 트랜잭션 경계에 대한 이해, 실패 케이스를 구체적으로 구분하는지",
    "history": "(none)",
}


def _settings(args: argparse.Namespace) -> Settings:
    over: dict[str, Any] = {}
    if args.base_url:
        over["llm_base_url"] = args.base_url
    if args.api_key is not None:
        over["llm_api_key"] = args.api_key
    if args.model:
        over["llm_pro_model"] = args.model
        over["llm_flash_model"] = args.model
    over["llm_pro_timeout_sec"] = args.timeout
    over["llm_flash_timeout_sec"] = args.timeout
    if args.max_tokens:
        over["llm_flash_max_tokens"] = args.max_tokens
    return Settings(**over)


def _patch_extra_body(chain: Any, extra_body: dict[str, Any] | None) -> None:
    """체인 안의 ChatOpenAI 에 extra_body 를 주입 (공급자 전용 옵션 실험용)."""
    if not extra_body:
        return
    from langchain_openai import ChatOpenAI

    def visit(node: Any) -> None:
        if isinstance(node, ChatOpenAI):
            node.extra_body = {**(node.extra_body or {}), **extra_body}
            return
        for attr in ("steps", "first", "middle", "last", "bound"):
            child = getattr(node, attr, None)
            if child is None:
                continue
            if isinstance(child, (list, tuple)):
                for c in child:
                    visit(c)
            else:
                visit(child)

    visit(chain)


async def bench_questions(
    settings: Settings, runs: int, extra_body: dict | None
) -> dict:
    chain = build_question_generation_chain(settings)
    _patch_extra_body(chain, extra_body)
    gen = LlmQuestionGenerator(chain)
    samples: list[dict[str, Any]] = []
    for i in range(runs):
        t0 = time.perf_counter()
        try:
            pool = await gen.generate(
                job_categories=["BACKEND"],
                mode="TECHNICAL",
                max_questions=3,
                context=SAMPLE_RESUME_MD,
            )
            elapsed = time.perf_counter() - t0
            samples.append(
                {
                    "ok": True,
                    "latency_sec": round(elapsed, 2),
                    "questions": [q.model_dump(by_alias=True) for q in pool.questions],
                }
            )
        except Exception as exc:  # noqa: BLE001 — 벤치는 실패 사유를 기록만 한다
            elapsed = time.perf_counter() - t0
            samples.append(
                {
                    "ok": False,
                    "latency_sec": round(elapsed, 2),
                    "error": f"{type(exc).__name__}: {str(exc)[:400]}",
                }
            )
        print(
            f"  questions run {i + 1}/{runs}: ok={samples[-1]['ok']} {samples[-1]['latency_sec']}s",
            file=sys.stderr,
        )
    return _summarize("questions", samples)


async def bench_followup(
    settings: Settings, runs: int, extra_body: dict | None
) -> dict:
    # 운영 경로(followup_consumer → StreamingFollowupGenerator.stream, <intent>/<question>/<meta> 태그)
    # 를 그대로 태운다. build_followup_generation_chain(JSON 파서) 는 운영에서 쓰이지 않는다.
    gen = build_streaming_followup_generator(settings)
    _patch_extra_body(gen._llm, extra_body)  # noqa: SLF001 — 벤치 전용 주입
    samples: list[dict[str, Any]] = []
    for i in range(runs):
        t0 = time.perf_counter()
        first_token_at: list[float] = []

        def on_token(_delta: str) -> None:
            if not first_token_at:
                first_token_at.append(time.perf_counter() - t0)

        try:
            res = await gen.stream(on_question_token=on_token, **SAMPLE_FOLLOWUP)
            elapsed = time.perf_counter() - t0
            samples.append(
                {
                    "ok": True,
                    "latency_sec": round(elapsed, 2),
                    "first_question_token_sec": (
                        round(first_token_at[0], 2) if first_token_at else None
                    ),
                    "followup_question": res.followup_question,
                    "answer_intent": res.answer_intent,
                    "answer_evaluation": (
                        res.answer_evaluation.model_dump(by_alias=True)
                        if res.answer_evaluation
                        else None
                    ),
                }
            )
        except Exception as exc:  # noqa: BLE001
            elapsed = time.perf_counter() - t0
            samples.append(
                {
                    "ok": False,
                    "latency_sec": round(elapsed, 2),
                    "error": f"{type(exc).__name__}: {str(exc)[:400]}",
                }
            )
        print(
            f"  followup run {i + 1}/{runs}: ok={samples[-1]['ok']} {samples[-1]['latency_sec']}s",
            file=sys.stderr,
        )
    return _summarize("followup", samples)


def _summarize(name: str, samples: list[dict[str, Any]]) -> dict:
    oks = [s for s in samples if s["ok"]]
    lat = [s["latency_sec"] for s in oks]
    return {
        "chain": name,
        "runs": len(samples),
        "success": len(oks),
        "latency_median_sec": round(statistics.median(lat), 2) if lat else None,
        "latency_max_sec": max(lat) if lat else None,
        "samples": samples,
    }


async def main() -> int:
    ap = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    ap.add_argument(
        "--base-url",
        default="",
        help="OpenAI 호환 base URL (기본: settings.llm_base_url)",
    )
    ap.add_argument(
        "--api-key", default=None, help="API key (Ollama 는 아무 값이나, 예: ollama)"
    )
    ap.add_argument(
        "--model", default="", help="pro/flash 둘 다 이 모델로 (기본: settings 값)"
    )
    ap.add_argument("--runs", type=int, default=2)
    ap.add_argument("--timeout", type=float, default=180.0)
    ap.add_argument(
        "--max-tokens", type=int, default=0, help="flash max_tokens 덮어쓰기 (0=기본)"
    )
    ap.add_argument("--extra-body", default="", help="JSON. 예: '{\"think\": false}'")
    ap.add_argument("--only", choices=["questions", "followup"], default=None)
    ap.add_argument("--out", default="", help="결과 JSON 저장 경로")
    args = ap.parse_args()

    settings = _settings(args)
    extra_body = json.loads(args.extra_body) if args.extra_body else None
    label = args.model or f"{settings.llm_pro_model}/{settings.llm_flash_model}"
    print(
        f"== bench model={label} base_url={settings.llm_base_url} runs={args.runs}",
        file=sys.stderr,
    )

    # 1회 워밍업 호출 지연(모델 로드)을 분리해 보기 위해 첫 run 도 그대로 기록한다.
    results: list[dict] = []
    if args.only in (None, "questions"):
        results.append(await bench_questions(settings, args.runs, extra_body))
    if args.only in (None, "followup"):
        results.append(await bench_followup(settings, args.runs, extra_body))

    report = {
        "model": label,
        "base_url": settings.llm_base_url,
        "extra_body": extra_body,
        "results": results,
    }
    text = json.dumps(report, ensure_ascii=False, indent=2)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"saved {args.out}", file=sys.stderr)
    else:
        print(text)
    for r in results:
        print(
            f"{r['chain']:<10} success={r['success']}/{r['runs']} "
            f"median={r['latency_median_sec']}s max={r['latency_max_sec']}s",
            file=sys.stderr,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
