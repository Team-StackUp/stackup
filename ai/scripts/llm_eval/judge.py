"""블라인드 LLM 판정: 같은 케이스에 대한 후보 모델 출력을 익명 라벨(A,B,C…)로 섞어 채점.

- 판정 모델 2개(서로 다른 계열)로 자기 계열 선호 편향을 상쇄: gemini-3.1-pro-preview, claude-opus-5
- 판정자는 어떤 모델의 출력인지 모른다. 순서는 판정 호출마다 무작위.

python judge.py --out /tmp/eval/judge.jsonl /tmp/eval/*.jsonl
"""

from __future__ import annotations

import argparse
import asyncio
import json
import os
import random
import re
import string
import sys
from collections import defaultdict

import httpx

sys.path.insert(0, os.path.dirname(__file__))
import importlib as _il  # noqa: E402

_C = _il.import_module(os.environ.get("LLM_EVAL_CASES", "cases"))  # noqa: E402
COACHING_CASES, FOLLOWUP_CASES, QUESTION_CASES = (
    _C.COACHING_CASES,
    _C.FOLLOWUP_CASES,
    _C.QUESTION_CASES,
)

JUDGES = ["gemini-3.1-pro-preview", "claude-opus-5"]

F_BY_ID = {c["id"]: c for c in FOLLOWUP_CASES}
Q_BY_ID = {c["id"]: c for c in QUESTION_CASES}
C_BY_ID = {c["id"]: c for c in COACHING_CASES}

SYSTEM = (
    "당신은 IT 기술면접 서비스의 품질 평가자입니다. 같은 입력에 대해 여러 익명 후보(A, B, C…)가 만든 "
    "출력을 비교 채점합니다. 후보가 어떤 모델인지 추측하지 말고, 출력 내용만 보고 엄격하고 일관되게 "
    "1~5 정수로 채점하세요 (5=실제 시니어 면접관 수준, 3=쓸 만하나 뚜렷한 약점, 1=사용 불가). "
    "반드시 JSON 만 출력합니다."
)

FOLLOWUP_RUBRIC = (
    "채점 기준 (각 1~5):\n"
    "- relevance: 지원자 답변의 특정 대목(수치·주장·기술 선택)을 짚는가. 답변 의도(모름/재설명 요청/확인형 단답)에 "
    "알맞게 대응하는가. 재설명 요청이면 직전 질문을 쉽게 다시 설명했는가.\n"
    "- depth: 기대 신호 중 놓친 부분이나 가장 약한 축을 파고들어 면접 변별력이 있는가. 이미 한 대화를 반복하지 않는가. "
    "사소하거나 뻔한 질문(예: 키의 길이·형식)은 낮게.\n"
    "- language: 자연스러운 한국어, 한 문장·간결(대략 60자 이내 권장), 오역·어색한 용어·외국 문자 혼입 없음.\n"
    "- overall: 실제 서비스에 그대로 내보낼 만한가 종합.\n"
)

QUESTIONS_RUBRIC = (
    "채점 기준 (각 1~5, 후보의 질문 풀 전체를 하나로 평가):\n"
    "- grounding: 모든 질문이 지원자 자료(자기소개·이력서·레포·자소서)의 구체적 사실에 근거하는가. 자료에 없는 "
    "내용을 지어내거나 누구나 답할 수 있는 교과서 질문이면 크게 감점. target_evidence 가 자료와 일치하는가.\n"
    "- coverage: 질문들이 서로 다른 주제를 다루고 요청한 면접 모드·직군(복수면 고르게)에 맞게 분배되었는가. "
    "최근 받은 질문과 중복되지 않는가.\n"
    "- depth: 실제 면접에서 역량을 변별할 수 있는 깊이인가.\n"
    "- language: 자연스러운 한국어, 간결(80자 내외), 오역·어색한 용어 없음.\n"
    "- overall: 실제 서비스에 그대로 내보낼 만한가 종합.\n"
)

COACHING_RUBRIC = (
    "채점 기준 (각 1~5):\n"
    "- usefulness: 지원자가 다음에 더 잘 답하도록 실질적이고 구체적인 방향을 주는가.\n"
    "- faithfulness: 지원자 자료/답변에 없는 경험·수치·실적을 사실처럼 지어내지 않았는가 (지어냈으면 1~2).\n"
    "- rewrite: answer_rewrite 가 지원자의 실제 답변을 출발점으로 개선했는가 (완전히 다른 답으로 대체하면 감점).\n"
    "- language: 자연스러운 한국어, coaching_comment 는 한 문장.\n"
    "- overall: 실제 서비스에 그대로 내보낼 만한가 종합.\n"
)


def _clip(s: str, n: int) -> str:
    return s if len(s) <= n else s[:n] + " …(생략)"


def build_items(recs_by_label: dict[str, list[dict]]):
    """(suite, case_id) → {label: output_text}"""
    items: dict[tuple[str, str], dict[str, str]] = defaultdict(dict)
    for label, recs in recs_by_label.items():
        for r in recs:
            if not r.get("ok") or r.get("rep") != 0:
                continue
            if r["suite"] == "followup":
                items[("followup", r["case_id"])][
                    label
                ] = f"[분류한 답변 의도] {r.get('answer_intent')}\n[꼬리질문] {r.get('followup_question')}"
            elif r["suite"] == "questions":
                lines = [
                    f"{i + 1}. ({q['category']}/{q.get('job_category')}) {q['question']}\n"
                    f"   근거: {q.get('target_evidence') or '(없음)'}"
                    for i, q in enumerate(r["questions"])
                ]
                items[("questions", r["case_id"])][label] = (
                    "\n".join(lines) or "(질문 0개)"
                )
            elif r["suite"] == "coaching":
                c = r["coaching"]
                items[("coaching", r["case_id"])][label] = (
                    f"[model_answer]\n{c.get('model_answer')}\n[answer_rewrite]\n{c.get('answer_rewrite')}\n"
                    f"[coaching_comment] {c.get('coaching_comment')}"
                )
    return items


def case_brief(suite: str, cid: str) -> str:
    if suite == "followup":
        c = F_BY_ID[cid]
        return (
            f"직군 {c['job_category']} / 모드 {c['mode']} / 직전 질문 카테고리 {c['parent_category']}\n"
            f"이미 나눈 대화:\n{c['history']}\n\n직전 질문: {c['previous_question']}\n"
            f"기대 신호: {c['expected_signal']}\n지원자 답변: {c['answer_text']}\n"
            f"검색 문서 컨텍스트:\n{_clip(c['context'], 1500)}"
        )
    if suite == "questions":
        c = Q_BY_ID[cid]
        return (
            f"직군 {', '.join(c['job_categories'])} / 모드 {c['mode']} / 요청 질문 수 {c['max_questions']}\n"
            f"자기소개: {c.get('self_introduction') or '(없음)'}\n"
            f"최근 받은 질문(중복 금지): {c.get('recent_questions') or '(없음)'}\n"
            f"타깃 회사/JD: {(c.get('target_company_name') or '') + ' ' + (c.get('target_job_description') or '(없음)')}\n"
            f"집중 영역: {c.get('focus_areas') or '(없음)'}\n"
            f"지원자 자료:\n{_clip(c['context'], 6000)}"
        )
    c = C_BY_ID[cid]
    return (
        f"직군 {c['job_category']} / 모드 {c['mode']}\n질문: {c['question']}\n기대 신호: {c['expected_signal']}\n"
        f"지원자 실제 답변: {c['answer']}\n지원자 자료:\n{_clip(c['rag_context'], 2500)}"
    )


RUBRIC = {
    "followup": FOLLOWUP_RUBRIC,
    "questions": QUESTIONS_RUBRIC,
    "coaching": COACHING_RUBRIC,
}
KEYS = {
    "followup": ["relevance", "depth", "language", "overall"],
    "questions": ["grounding", "coverage", "depth", "language", "overall"],
    "coaching": ["usefulness", "faithfulness", "rewrite", "language", "overall"],
}


async def judge_one(
    client, base_url, api_key, judge, suite, cid, cands: dict[str, str], ordering=0
):
    # 재현 가능한 순서: (판정자, 과제, 케이스) 로 시드. ordering 1 은 같은 순서의 역순 → 위치 편향 상쇄.
    labels = sorted(cands)
    random.Random(f"{judge}|{suite}|{cid}").shuffle(labels)
    if ordering % 2 == 1:
        labels = labels[::-1]
    letters = list(string.ascii_uppercase[: len(labels)])
    mapping = dict(zip(letters, labels))
    blocks = "\n\n".join(f"### 후보 {L}\n{cands[mapping[L]]}" for L in letters)
    keys = KEYS[suite]
    schema = (
        '{"rationale": "채점 근거 1~2문장 (점수보다 먼저)", '
        + ", ".join(f'"{k}": 1-5' for k in keys)
        + ', "issue": "가장 큰 문제 한 줄"}'
    )
    user = (
        f"## 입력\n{case_brief(suite, cid)}\n\n## {RUBRIC[suite]}\n## 후보 출력\n{blocks}\n\n"
        f'## 출력 형식\n{{"ratings": {{"A": {schema}, "B": ...}}}} — 모든 후보({", ".join(letters)})를 빠짐없이.'
    )
    body = {
        "model": judge,
        "messages": [
            {"role": "system", "content": SYSTEM},
            {"role": "user", "content": user},
        ],
        "max_tokens": 16000,
        "temperature": 0,
    }
    for attempt in range(3):
        try:
            resp = await client.post(
                f"{base_url}/chat/completions",
                headers={"Authorization": f"Bearer {api_key}"},
                json=body,
                timeout=300,
            )
            resp.raise_for_status()
            text = resp.json()["choices"][0]["message"]["content"] or ""
            m = re.search(r"\{.*\}", text, re.S)
            data = json.loads(m.group(0))["ratings"]
            return {
                "judge": judge,
                "suite": suite,
                "case_id": cid,
                "ratings": {mapping[L]: data[L] for L in letters if L in data},
                "positions": {mapping[L]: i for i, L in enumerate(letters)},
                "ordering": ordering,
                "output_chars": {mapping[L]: len(cands[mapping[L]]) for L in letters},
                "n_candidates": len(letters),
            }
        except Exception as exc:  # noqa: BLE001
            err = f"{type(exc).__name__}: {str(exc)[:200]}"
            await asyncio.sleep(3 * (attempt + 1))
    return {"judge": judge, "suite": suite, "case_id": cid, "error": err}


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--exclude", default="", help="쉼표로 구분한 label 제외")
    ap.add_argument("--concurrency", type=int, default=4)
    ap.add_argument(
        "--judges", default=",".join(JUDGES), help="쉼표로 구분한 판정 모델"
    )
    ap.add_argument(
        "--orderings",
        type=int,
        default=1,
        help="후보 제시 순서 수 (2 = 시드 순서 + 역순)",
    )
    ap.add_argument("paths", nargs="+")
    args = ap.parse_args()
    exclude = {x for x in args.exclude.split(",") if x}
    judges = [j for j in args.judges.split(",") if j]

    recs_by_label: dict[str, list[dict]] = defaultdict(list)
    for p in args.paths:
        with open(p, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    r = json.loads(line)
                    if r["label"] not in exclude:
                        recs_by_label[r["label"]].append(r)
    items = build_items(recs_by_label)
    base_url = os.environ["LLM_BASE_URL"].rstrip("/")
    api_key = os.environ["LLM_API_KEY"]
    sem = asyncio.Semaphore(args.concurrency)
    async with httpx.AsyncClient() as client:

        async def run(judge, key, cands, ordering=0):
            async with sem:
                res = await judge_one(
                    client, base_url, api_key, judge, key[0], key[1], cands, ordering
                )
                print(
                    f"  {judge:<24} {key[0]:<9} {key[1]:<28} {'ERR ' + res['error'] if 'error' in res else 'ok'}",
                    file=sys.stderr,
                )
                return res

        tasks = [
            run(j, k, c, o)
            for k, c in sorted(items.items())
            for j in judges
            for o in range(args.orderings)
            if len(c) >= 2
        ]
        results = await asyncio.gather(*tasks)
    with open(args.out, "w", encoding="utf-8") as f:
        for r in results:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    # 집계: label × suite × judge 평균, 그리고 두 판정자 평균
    agg: dict = defaultdict(lambda: defaultdict(lambda: defaultdict(list)))
    for r in results:
        for label, sc in (r.get("ratings") or {}).items():
            try:
                agg[label][r["suite"]][r["judge"]].append(float(sc["overall"]))
            except Exception:  # noqa: BLE001
                pass
    table = {}
    for label, suites in agg.items():
        table[label] = {}
        for suite, judges in suites.items():
            per = {j: round(sum(v) / len(v), 2) for j, v in judges.items() if v}
            table[label][suite] = {
                **per,
                "mean": round(sum(per.values()) / len(per), 2) if per else None,
            }
    print(json.dumps(table, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    asyncio.run(main())
