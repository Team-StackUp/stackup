"""정답 라벨 독립 검증 — LLM 을 블라인드 2차 주석자로 사용 (인간 검증 불가 시 대체, 한계 명시).

케이스를 작성한 모델(Anthropic 계열)과 다른 계열 LLM 에게 제안 라벨을 보여주지 않고 라벨을 매기게 한 뒤,
제안 라벨과의 Cohen's κ(의도, 사실 일치)·가중 κ(구체성 점수대)를 계산한다.
주석 기준 문장은 human-eval/README.md 1단계와 같다.

LLM_EVAL_CASES=cases_v2 python label_annotate.py --annotators gpt-5.5,gemini-3.1-pro-preview --out labels.jsonl
"""

from __future__ import annotations

import argparse
import asyncio
import importlib
import json
import os
import re
import sys
from collections import Counter

import httpx

sys.path.insert(0, os.path.dirname(__file__))
_C = importlib.import_module(os.environ.get("LLM_EVAL_CASES", "cases"))  # noqa: E402

GUIDE = (
    "당신은 IT 기술면접 데이터의 라벨링 검증자입니다. 면접관의 직전 질문과 지원자 답변을 읽고 아래 기준으로 라벨을 매기세요.\n"
    "1) intent: NORMAL / DONT_KNOW / CLARIFICATION\n"
    "   - DONT_KNOW: '모르겠습니다·패스·기억 안 남' 등 사실상 답을 못 함.\n"
    "   - CLARIFICATION: 답 대신 질문을 다시·쉽게 설명해 달라고 함.\n"
    "   - 그 외 NORMAL.\n"
    "2) specificity_band: HIGH / LOW / NOT_SCORED / NA\n"
    "   - intent 가 NORMAL 일 때만. 수치·사례·선택 근거가 분명하면 HIGH(0~5 중 3 이상), 추상적이면 LOW(2 이하).\n"
    "   - 확인형 질문에 대한 짧은 단답·정정은 NOT_SCORED.\n"
    "   - intent 가 DONT_KNOW 또는 CLARIFICATION 이면 NA.\n"
    "3) correctness_band: UNJUDGEABLE / MISMATCH / MATCH / NA\n"
    "   - 참고 자료가 비어 있으면 UNJUDGEABLE. 자료와 답변이 어긋나면 MISMATCH, 맞으면 MATCH.\n"
    "   - intent 가 DONT_KNOW 또는 CLARIFICATION 이면 NA.\n"
    "4) realism: 1~5 (실제 IT 면접에서 나올 법한 질문·답변인가)\n"
    'JSON 만 출력: {"intent": "...", "specificity_band": "...", "correctness_band": "...", "realism": 1-5, "note": "한 줄"}'
)

INTENT = {
    "NORMAL": "NORMAL",
    "DONT_KNOW": "DONT_KNOW",
    "CLARIFICATION": "CLARIFICATION",
}
SPEC = {"high": "HIGH", "low": "LOW", "null": "NOT_SCORED", None: "NA"}
CORR = {"null": "UNJUDGEABLE", "low": "MISMATCH", "high": "MATCH", None: "NA"}


def gold(case: dict) -> dict:
    intent = case["expect_intent"]
    spec = SPEC[case.get("expect_scores")] if intent == "NORMAL" else "NA"
    corr = CORR[case.get("expect_correctness")] if intent == "NORMAL" else "NA"
    return {"intent": intent, "specificity_band": spec, "correctness_band": corr}


def kappa(a: list[str], b: list[str]) -> float:
    n = len(a)
    cats = sorted(set(a) | set(b))
    po = sum(x == y for x, y in zip(a, b)) / n
    ca, cb = Counter(a), Counter(b)
    pe = sum(ca[c] * cb[c] for c in cats) / (n * n)
    return 1.0 if pe == 1 else (po - pe) / (1 - pe)


async def annotate(client, base, key, model, case) -> dict:
    user = (
        f"직군: {case['job_category']} / 모드: {case['mode']} / 직전 질문 카테고리: {case['parent_category']}\n"
        f"대화 이력:\n{case['history']}\n\n직전 질문: {case['previous_question']}\n"
        f"기대 신호: {case['expected_signal']}\n지원자 답변: {case['answer_text']}\n"
        f"참고 자료:\n{case['context'] if case['context'] != '(none)' else '(비어 있음)'}"
    )
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": GUIDE},
            {"role": "user", "content": user},
        ],
        "max_tokens": 4000,
        "temperature": 0,
    }
    for attempt in range(3):
        try:
            r = await client.post(
                f"{base}/chat/completions",
                headers={"Authorization": f"Bearer {key}"},
                json=body,
                timeout=180,
            )
            if r.status_code in (402, 403):
                raise SystemExit(f"STOP quota/auth {r.status_code}: {r.text[:200]}")
            r.raise_for_status()
            text = r.json()["choices"][0]["message"]["content"] or ""
            data = json.loads(re.search(r"\{.*\}", text, re.S).group(0))
            return {"annotator": model, "case_id": case["id"], **data}
        except SystemExit:
            raise
        except Exception as exc:  # noqa: BLE001
            err = f"{type(exc).__name__}: {str(exc)[:150]}"
            await asyncio.sleep(3 * (attempt + 1))
    return {"annotator": model, "case_id": case["id"], "error": err}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--annotators", default="gpt-5.5,gemini-3.1-pro-preview")
    ap.add_argument("--concurrency", type=int, default=4)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    base = os.environ["LLM_BASE_URL"].rstrip("/")
    key = os.environ["LLM_API_KEY"]
    cases = list(_C.FOLLOWUP_CASES)
    sem = asyncio.Semaphore(args.concurrency)
    async with httpx.AsyncClient() as client:

        async def run(m, c):
            async with sem:
                return await annotate(client, base, key, m, c)

        results = await asyncio.gather(
            *(run(m, c) for m in args.annotators.split(",") for c in cases)
        )
    with open(args.out, "w", encoding="utf-8") as f:
        for r in results:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    by = {(r["annotator"], r["case_id"]): r for r in results if "error" not in r}
    ann = args.annotators.split(",")
    report = {
        "n_cases": len(cases),
        "errors": sum("error" in r for r in results),
        "vs_gold": {},
        "between": {},
    }
    for field in ("intent", "specificity_band", "correctness_band"):
        g = [gold(c)[field] for c in cases]
        for m in ann:
            labs = [
                str(by.get((m, c["id"]), {}).get(field, "MISSING")).upper()
                for c in cases
            ]
            report["vs_gold"].setdefault(field, {})[m] = {
                "agreement": round(
                    sum(x == y for x, y in zip(g, labs)) / len(cases), 3
                ),
                "kappa": round(kappa(g, labs), 3),
                "disagree": [c["id"] for c, x, y in zip(cases, g, labs) if x != y],
            }
        if len(ann) >= 2:
            a = [
                str(by.get((ann[0], c["id"]), {}).get(field, "MISSING")).upper()
                for c in cases
            ]
            b = [
                str(by.get((ann[1], c["id"]), {}).get(field, "MISSING")).upper()
                for c in cases
            ]
            report["between"][field] = {
                "agreement": round(sum(x == y for x, y in zip(a, b)) / len(cases), 3),
                "kappa": round(kappa(a, b), 3),
            }
    real = [
        by[(m, c["id"])].get("realism")
        for m in ann
        for c in cases
        if (m, c["id"]) in by
    ]
    real = [float(x) for x in real if isinstance(x, (int, float))]
    report["realism_mean"] = round(sum(real) / len(real), 2) if real else None
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
