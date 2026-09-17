"""개방형(open-loop) Poisson 도착 부하로 꼬리질문 스트리밍 지연을 측정한다 (RQ3).

운영 꼬리질문 프롬프트(SYSTEM/HUMAN)를 그대로 렌더링해 OpenAI 호환 스트리밍 API 로 직접 보내고,
요청별 도착 시각·첫 토큰 시각·토큰 간 간격·종료 시각·출력 토큰 수를 기록한다.
응답을 기다리지 않고 도착 시각표대로 보내므로 폐쇄형 부하가 숨기는 꼬리 지연이 드러난다
(Schroeder et al., NSDI 2006).

python latency_bench.py --label lcpp-gemma4-e2b --base-url http://stackup-llmtest:8080/v1 \
    --model testmodel --rates 0.1,0.3,0.6 --duration 60 --reps 3 --cache on --out /tmp/lat/x.jsonl
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

from ai_server.chain.prompts.followup_generation import (  # noqa: E402
    HUMAN_PROMPT,
    SYSTEM_PROMPT,
)
from langchain_core.prompts import ChatPromptTemplate  # noqa: E402

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


def render(case: dict) -> list[dict]:
    msgs = TEMPLATE.format_messages(**{k: case[k] for k in VARS})
    return [
        {"role": "system" if m.type == "system" else "user", "content": m.content}
        for m in msgs
    ]


async def one(
    client, url, model, case, cache: bool, extra: dict, t_arrival: float, t0: float
) -> dict:
    body = {
        "model": model,
        "messages": render(case),
        "stream": True,
        "stream_options": {"include_usage": True},
        "temperature": 0.4,
        "max_tokens": 512,
        "cache_prompt": cache,
        **extra,
    }
    rec = {"case_id": case["id"], "arrival": round(t_arrival - t0, 3)}
    first = None
    gaps: list[float] = []
    last = None
    n_chunks = 0
    usage = None
    try:
        async with client.stream("POST", url, json=body, timeout=600) as resp:
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
                choices = obj.get("choices") or []
                if not choices:
                    continue
                piece = (choices[0].get("delta") or {}).get("content") or ""
                if not piece:
                    continue
                now = time.perf_counter()
                if first is None:
                    first = now
                else:
                    gaps.append(now - last)
                last = now
                n_chunks += 1
        end = time.perf_counter()
        out_tokens = (usage or {}).get("completion_tokens") or n_chunks
        rec.update(
            ok=True,
            ttft=round(first - t_arrival, 4) if first else None,
            e2e=round(end - t_arrival, 4),
            out_tokens=out_tokens,
            in_tokens=(usage or {}).get("prompt_tokens"),
            tpot=round((end - first) / max(1, out_tokens - 1), 4) if first else None,
            tbt_p90=(
                round(sorted(gaps)[int(0.9 * (len(gaps) - 1))], 4) if gaps else None
            ),
            tbt_max=round(max(gaps), 4) if gaps else None,
            t_end=round(end - t0, 3),
        )
    except Exception as exc:  # noqa: BLE001
        rec.update(
            ok=False,
            error=f"{type(exc).__name__}: {str(exc)[:200]}",
            e2e=round(time.perf_counter() - t_arrival, 4),
        )
    return rec


def pct(xs, q):
    xs = sorted(x for x in xs if x is not None)
    return round(xs[min(len(xs) - 1, int(round(q * (len(xs) - 1))))], 3) if xs else None


async def run_rate(
    args, client, rate: float, rep: int, cases: list[dict], extra: dict, fh
) -> None:
    url = args.base_url.rstrip("/") + "/chat/completions"
    rng = random.Random(f"{args.label}|{rate}|{rep}")
    # 도착 시각표를 먼저 만든다 (응답과 무관)
    arrivals, t = [], 0.0
    while True:
        t += rng.expovariate(rate)
        if t > args.duration:
            break
        arrivals.append(t)
    t0 = time.perf_counter()
    wall_t0 = time.time()
    tasks = []
    for i, a in enumerate(arrivals):
        delay = a - (time.perf_counter() - t0)
        if delay > 0:
            await asyncio.sleep(delay)
        case = cases[(rep * 7 + i) % len(cases)]
        tasks.append(
            asyncio.create_task(
                one(
                    client,
                    url,
                    args.model,
                    case,
                    args.cache == "on",
                    extra,
                    time.perf_counter(),
                    t0,
                )
            )
        )
    recs = await asyncio.gather(*tasks)
    wall = time.perf_counter() - t0
    for r in recs:
        r.update(label=args.label, rate=rate, rep=rep, cache=args.cache, kind="request")
        fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    ok = [r for r in recs if r.get("ok")]
    good = [
        r
        for r in ok
        if r["ttft"] is not None
        and r["ttft"] < args.slo_ttft
        and r["e2e"] < args.slo_e2e
    ]
    summ = {
        "label": args.label,
        "kind": "summary",
        "rate": rate,
        "rep": rep,
        "cache": args.cache,
        "wall_start_epoch": round(wall_t0, 3),
        "wall_sec": round(wall, 2),
        "requests": len(recs),
        "ok": len(ok),
        "ttft_p50": pct([r["ttft"] for r in ok], 0.5),
        "ttft_p90": pct([r["ttft"] for r in ok], 0.9),
        "e2e_p50": pct([r["e2e"] for r in ok], 0.5),
        "e2e_p90": pct([r["e2e"] for r in ok], 0.9),
        "tpot_mean": (
            round(statistics.mean([r["tpot"] for r in ok if r["tpot"]]), 4)
            if ok
            else None
        ),
        "tbt_p90_median": pct([r["tbt_p90"] for r in ok], 0.5),
        "goodput_ratio": round(len(good) / len(recs), 3) if recs else None,
        "out_tokens_total": sum(r.get("out_tokens") or 0 for r in ok),
        "in_tokens_mean": (
            round(
                statistics.mean([r["in_tokens"] for r in ok if r.get("in_tokens")]), 1
            )
            if any(r.get("in_tokens") for r in ok)
            else None
        ),
    }
    fh.write(json.dumps(summ, ensure_ascii=False) + "\n")
    fh.flush()
    print(
        "  ",
        {
            k: v
            for k, v in summ.items()
            if k not in ("label", "kind", "wall_start_epoch")
        },
        file=sys.stderr,
    )


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--base-url", required=True)
    ap.add_argument("--model", default="testmodel")
    ap.add_argument("--rates", default="0.1,0.3,0.6", help="초당 도착률 λ 목록")
    ap.add_argument("--duration", type=float, default=60.0)
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--cache", choices=["on", "off"], default="on")
    ap.add_argument("--slo-ttft", type=float, default=2.0)
    ap.add_argument("--slo-e2e", type=float, default=4.0)
    ap.add_argument("--extra-body", default="")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    extra = json.loads(args.extra_body) if args.extra_body else {}
    cases = [c for c in _C.FOLLOWUP_CASES]
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    print(
        f"== latency {args.label} cache={args.cache} rates={args.rates}",
        file=sys.stderr,
    )
    async with httpx.AsyncClient(timeout=600) as client:
        url = args.base_url.rstrip("/") + "/chat/completions"
        for c in cases[:3]:  # 워밍업 (기록 안 함)
            await one(
                client,
                url,
                args.model,
                c,
                True,
                extra,
                time.perf_counter(),
                time.perf_counter(),
            )
        with open(args.out, "a", encoding="utf-8") as fh:
            rates = [float(x) for x in args.rates.split(",")]
            for rep in range(args.reps):  # 반복마다 도착률 순서를 교차
                order = rates if rep % 2 == 0 else list(reversed(rates))
                for rate in order:
                    await run_rate(args, client, rate, rep, cases, extra, fh)


if __name__ == "__main__":
    asyncio.run(main())
