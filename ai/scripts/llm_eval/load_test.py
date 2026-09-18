"""동시 사용자 부하 테스트 — 운영 스트리밍 꼬리질문·코칭 체인으로 동시성 N 에서 지연 분포를 잰다.

python load_test.py --label local-llamacpp-np4 --base-url http://host:port/v1 --api-key x \
    --model qwen3-4b --concurrency 1,2,4,8 --requests-per-level 16 --out /tmp/eval/load.jsonl

시나리오
- followup@N : 꼬리질문 요청을 동시에 N 개씩 흘려 보냄 (서로 다른 케이스 순환). 요청별 총지연·첫 질문 토큰.
- mixed      : 코칭 15건×동시 5 fan-out 을 돌리며 1.5초 간격으로 꼬리질문 6건 투입 → 꼬리질문 지연 분포.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import sys
import time

sys.path.insert(0, os.path.dirname(__file__))

import run_eval as R  # noqa: E402
from cases import COACHING_CASES, FOLLOWUP_CASES  # noqa: E402


def pct(xs, q):
    xs = sorted(x for x in xs if x is not None)
    if not xs:
        return None
    return round(xs[min(len(xs) - 1, int(round(q * (len(xs) - 1))))], 2)


async def level(settings, label, n, total, fh):
    sem = asyncio.Semaphore(n)
    cases = [FOLLOWUP_CASES[i % len(FOLLOWUP_CASES)] for i in range(total)]

    async def one(i, c):
        async with sem:
            return await R.run_followup(settings, c, i, label, tag=f":load@{n}")

    t0 = time.perf_counter()
    recs = await asyncio.gather(*(one(i, c) for i, c in enumerate(cases)))
    wall = time.perf_counter() - t0
    for r in recs:
        fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    ok = [r for r in recs if r["ok"]]
    normal = [r for r in ok if r.get("answer_intent") == "NORMAL"]
    summary = {
        "label": label,
        "suite": "load_summary",
        "ok": True,
        "concurrency": n,
        "requests": total,
        "success": len(ok),
        "wall_sec": round(wall, 2),
        "throughput_req_per_min": round(60 * len(ok) / wall, 1),
        "latency_p50": pct([r["latency_sec"] for r in ok], 0.5),
        "latency_p95": pct([r["latency_sec"] for r in ok], 0.95),
        "ttft_p50": pct([r["ttft_sec"] for r in normal], 0.5),
        "ttft_p95": pct([r["ttft_sec"] for r in normal], 0.95),
        "over_10s": sum(1 for r in ok if r["latency_sec"] > 10),
    }
    fh.write(json.dumps(summary, ensure_ascii=False) + "\n")
    fh.flush()
    print(
        "  ",
        {k: v for k, v in summary.items() if k not in ("label", "suite", "ok")},
        file=sys.stderr,
    )


async def mixed(settings, label, fh):
    sem = asyncio.Semaphore(5)

    async def coach(i):
        async with sem:
            return await R.run_coaching(
                settings, COACHING_CASES[i % 3], i, label, tag=":mixed-fanout"
            )

    async def followups():
        out = []
        for i in range(6):
            await asyncio.sleep(1.5)
            out.append(
                asyncio.create_task(
                    R.run_followup(
                        settings, FOLLOWUP_CASES[i], i, label, tag=":mixed-followup"
                    )
                )
            )
        return await asyncio.gather(*out)

    t0 = time.perf_counter()
    res = await asyncio.gather(*(coach(i) for i in range(15)), followups())
    wall = time.perf_counter() - t0
    coaches, fus = res[:15], res[15]
    for r in list(coaches) + list(fus):
        fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    summary = {
        "label": label,
        "suite": "mixed_summary",
        "ok": True,
        "fanout_wall_sec": round(wall, 2),
        "coaching_success": sum(r["ok"] for r in coaches),
        "followup_latency_p50": pct([r["latency_sec"] for r in fus if r["ok"]], 0.5),
        "followup_latency_max": pct([r["latency_sec"] for r in fus if r["ok"]], 1.0),
        "followup_ttft_p50": pct([r["ttft_sec"] for r in fus if r["ok"]], 0.5),
    }
    fh.write(json.dumps(summary, ensure_ascii=False) + "\n")
    fh.flush()
    print(
        "   mixed",
        {k: v for k, v in summary.items() if k not in ("label", "suite", "ok")},
        file=sys.stderr,
    )


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--label", required=True)
    ap.add_argument("--model", required=True)
    ap.add_argument("--base-url", default="")
    ap.add_argument("--api-key", default=None)
    ap.add_argument("--concurrency", default="1,2,4,8")
    ap.add_argument("--requests-per-level", type=int, default=16)
    ap.add_argument("--timeout", type=float, default=300)
    ap.add_argument("--flash-max-tokens", type=int, default=0)
    ap.add_argument("--extra-body", default="")
    ap.add_argument("--skip-mixed", action="store_true")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    args.latency = False
    settings = R.make_settings(args)
    if args.extra_body:
        R.EXTRA_BODY.update(json.loads(args.extra_body))
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    print(
        f"== load {args.label} model={args.model} base={settings.llm_base_url}",
        file=sys.stderr,
    )
    with open(args.out, "a", encoding="utf-8") as fh:
        await R.run_followup(settings, FOLLOWUP_CASES[0], -1, args.label)  # 워밍업
        for n in [int(x) for x in args.concurrency.split(",")]:
            await level(
                settings, args.label, n, max(args.requests_per_level, n * 2), fh
            )
        if not args.skip_mixed:
            await mixed(settings, args.label, fh)


if __name__ == "__main__":
    asyncio.run(main())
