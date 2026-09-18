"""입력 길이에 따른 첫 토큰 지연(TTFT) 스케일링 측정 (RQ3·RQ5).

운영 꼬리질문 프롬프트의 참고 자료(context) 자리를 한국어 기술 문서로 채워 입력 길이만 바꾼다.
프리픽스 캐시를 끄고 한 번에 한 요청만 보내 큐 대기를 배제하므로, TTFT 기울기가 곧 프리필 처리율이다.
같은 한국어 원문이 모델마다 몇 토큰이 되는지도 함께 기록해 토크나이저 효율이 지연으로 번역되는 양을 잰다.

python prefill_bench.py --label qwen3-4b-q4 --base-url http://stackup-llmtest:8080/v1 \
    --corpus /q2/ko_eval.txt --chars 400,1200,3000,6000,12000,24000 --reps 5 --out /tmp/pf/x.jsonl
"""

from __future__ import annotations

import argparse
import asyncio
import importlib
import json
import os
import random
import statistics
import sys
import time

import httpx

sys.path.insert(0, os.path.dirname(__file__))
_C = importlib.import_module(os.environ.get("LLM_EVAL_CASES", "cases"))  # noqa: E402

from langchain_core.prompts import ChatPromptTemplate  # noqa: E402

from ai_server.chain.prompts.followup_generation import (  # noqa: E402
    HUMAN_PROMPT,
    SYSTEM_PROMPT,
)

TEMPLATE = ChatPromptTemplate.from_messages(
    [("system", SYSTEM_PROMPT), ("human", HUMAN_PROMPT)]
)
VARS = (
    "job_category",
    "mode",
    "previous_question",
    "answer_text",
    "context",
    "parent_category",
    "expected_signal",
    "history",
)


def load_corpus(path: str) -> str:
    with open(path, encoding="utf-8", errors="ignore") as f:
        text = " ".join(line.strip() for line in f if line.strip())
    return text


def build(case: dict, corpus: str, chars: int, seed: int) -> list[dict]:
    """참고 자료 자리를 corpus 로 채워 목표 글자 수를 맞춘다 (요청마다 다른 구간)."""
    rng = random.Random(seed)
    start = rng.randrange(0, max(1, len(corpus) - chars - 1))
    filler = (corpus * (chars // max(1, len(corpus)) + 2))[start : start + chars]
    c = dict(case)
    c["context"] = filler
    msgs = TEMPLATE.format_messages(**{k: c[k] for k in VARS})
    return [
        {"role": "system" if m.type == "system" else "user", "content": m.content}
        for m in msgs
    ]


async def one(client, url, model, messages, extra: dict) -> dict:
    body = {
        "model": model,
        "messages": messages,
        "stream": True,
        "stream_options": {"include_usage": True},
        "temperature": 0.0,
        "max_tokens": 8,
        "cache_prompt": False,
        **extra,
    }
    t0 = time.perf_counter()
    first = None
    usage = None
    try:
        async with client.stream("POST", url, json=body, timeout=900) as resp:
            resp.raise_for_status()
            async for line in resp.aiter_lines():
                if not line.startswith("data: "):
                    continue
                data = line[6:]
                if data.strip() == "[DONE]":
                    break
                obj = json.loads(data)
                if obj.get("usage"):
                    usage = obj["usage"]
                ch = obj.get("choices") or []
                if ch and (ch[0].get("delta") or {}).get("content") and first is None:
                    first = time.perf_counter()
        return {
            "ok": True,
            "ttft": round((first or time.perf_counter()) - t0, 4),
            "total": round(time.perf_counter() - t0, 4),
            "in_tokens": (usage or {}).get("prompt_tokens"),
        }
    except Exception as exc:  # noqa: BLE001
        return {"ok": False, "error": f"{type(exc).__name__}: {str(exc)[:200]}"}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--base-url", required=True)
    ap.add_argument("--model", default="testmodel")
    ap.add_argument("--corpus", required=True, help="한국어 텍스트 파일")
    ap.add_argument("--chars", default="400,1200,3000,6000,12000,24000")
    ap.add_argument("--reps", type=int, default=5)
    ap.add_argument("--extra-body", default="")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    extra = json.loads(args.extra_body) if args.extra_body else {}
    corpus = load_corpus(args.corpus)
    case = _C.FOLLOWUP_CASES[0]
    url = args.base_url.rstrip("/") + "/chat/completions"
    lengths = [int(x) for x in args.chars.split(",")]
    plan = [(c, r) for c in lengths for r in range(args.reps)]
    random.Random(f"{args.label}|plan").shuffle(plan)  # 길이 순서 효과 제거
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    async with httpx.AsyncClient(timeout=900) as client:
        await one(client, url, args.model, build(case, corpus, 400, 0), extra)  # 워밍업
        with open(args.out, "a", encoding="utf-8") as fh:
            for chars, rep in plan:
                msgs = build(case, corpus, chars, seed=chars * 100 + rep)
                rec = await one(client, url, args.model, msgs, extra)
                rec.update(label=args.label, chars=chars, rep=rep)
                fh.write(json.dumps(rec, ensure_ascii=False) + "\n")
                fh.flush()
                print(
                    f"  {args.label} chars={chars:6d} rep={rep} "
                    f"tok={rec.get('in_tokens')} ttft={rec.get('ttft')}"
                    f"{' ERR ' + rec.get('error', '') if not rec.get('ok') else ''}",
                    file=sys.stderr,
                )
    rows = [
        json.loads(x)
        for x in open(args.out, encoding="utf-8")
        if json.loads(x).get("label") == args.label and json.loads(x).get("ok")
    ]
    by: dict[int, list[float]] = {}
    for r in rows:
        by.setdefault(r["chars"], []).append(r["ttft"])
    print(f"== {args.label}", file=sys.stderr)
    for c in sorted(by):
        tok = [r["in_tokens"] for r in rows if r["chars"] == c and r["in_tokens"]]
        print(
            f"   chars={c:6d} tokens={statistics.median(tok) if tok else '?':>7} "
            f"ttft_median={statistics.median(by[c]):.3f} n={len(by[c])}",
            file=sys.stderr,
        )


if __name__ == "__main__":
    asyncio.run(main())
