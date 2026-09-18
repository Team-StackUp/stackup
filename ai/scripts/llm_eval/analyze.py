"""run_eval.py 가 남긴 JSONL 들을 자동 지표로 집계한다 (LLM 판정 없이 규칙 기반).

python analyze.py /tmp/eval/*.jsonl > summary.json
"""

from __future__ import annotations

import json
import os
import re
import statistics
import sys
from collections import defaultdict
from typing import Any

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import importlib as _il  # noqa: E402

_C = _il.import_module(os.environ.get("LLM_EVAL_CASES", "cases"))  # noqa: E402
COACHING_CASES, FOLLOWUP_CASES, QUESTION_CASES = (
    _C.COACHING_CASES,
    _C.FOLLOWUP_CASES,
    _C.QUESTION_CASES,
)

HAN = re.compile(r"[一-鿿㐀-䶿]")
KANA = re.compile(r"[぀-ヿ]")
OTHER_SCRIPT = re.compile(r"[Ѐ-ӿ฀-๿؀-ۿ]")  # 키릴·태국·아랍
HANGUL = re.compile(r"[가-힣]")
MARKDOWN = re.compile(r"(\*\*|^#+\s|`|^\s*[-*]\s)", re.M)
DIGITS = re.compile(r"\d+(?:\.\d+)?")

F_BY_ID = {c["id"]: c for c in FOLLOWUP_CASES}
Q_BY_ID = {c["id"]: c for c in QUESTION_CASES}
C_BY_ID = {c["id"]: c for c in COACHING_CASES}


def pct(n: int, d: int) -> float | None:
    return round(100.0 * n / d, 1) if d else None


def med(xs: list[float]) -> float | None:
    xs = [x for x in xs if x is not None]
    return round(statistics.median(xs), 2) if xs else None


def p90(xs: list[float]) -> float | None:
    xs = sorted(x for x in xs if x is not None)
    if not xs:
        return None
    return round(xs[min(len(xs) - 1, int(round(0.9 * (len(xs) - 1))))], 2)


def script_issue(text: str) -> bool:
    return bool(HAN.search(text) or KANA.search(text) or OTHER_SCRIPT.search(text))


def ngrams(s: str, n: int = 2) -> set[str]:
    s = re.sub(r"\s+", "", s)
    return {s[i : i + n] for i in range(max(0, len(s) - n + 1))}


def jaccard(a: str, b: str) -> float:
    A, B = ngrams(a), ngrams(b)
    return len(A & B) / len(A | B) if A and B else 0.0


def grounded(evidence: str, context: str) -> bool:
    """근거 인용이 컨텍스트에 실제로 있는가 (공백 제거 후 4-gram 60% 이상 일치)."""
    ev = re.sub(r"\s+", "", evidence)
    ctx = re.sub(r"\s+", "", context)
    grams = [ev[i : i + 4] for i in range(max(0, len(ev) - 3))]
    if not grams:
        return False
    return sum(1 for g in grams if g in ctx) / len(grams) >= 0.6


def analyze_followup(recs: list[dict]) -> dict[str, Any]:
    main = [r for r in recs if r["suite"] == "followup"]
    ok = [r for r in main if r["ok"]]
    out: dict[str, Any] = {"calls": len(main), "success_pct": pct(len(ok), len(main))}

    tag_ok = intent_hit = 0
    score_checks = score_hits = corr_checks = corr_hits = 0
    lengths: list[int] = []
    script_bad = 0
    per_case_intent: dict[str, list[str]] = defaultdict(list)
    spec_by_case: dict[str, list[float]] = defaultdict(list)
    for r in ok:
        case = F_BY_ID[r["case_id"]]
        q = r.get("followup_question") or ""
        ev = r.get("answer_evaluation")
        # 태그 준수: 질문에 태그 잔해가 없고, 평가 meta 파싱 성공(모름/재설명 제외 시 필수)
        tags_clean = "<" not in q and len(q) < 400
        # 확인형 단답은 프롬프트가 점수 null 을 지시 → AnswerEvaluation(specificity: float) 검증에서
        # meta 가 None 이 되는 것이 운영 정상 동작이므로 준수로 본다.
        meta_optional = (
            case["expect_intent"] != "NORMAL" or case.get("expect_scores") == "null"
        )
        if tags_clean and (ev is not None or meta_optional):
            tag_ok += 1
        per_case_intent[case["id"]].append(r.get("answer_intent"))
        if r.get("answer_intent") == case["expect_intent"]:
            intent_hit += 1
        lengths.append(len(q))
        if script_issue(q):
            script_bad += 1
        spec = ev.get("specificity") if ev else None
        if spec is not None:
            spec_by_case[case["id"]].append(spec)
        exp = case.get("expect_scores")
        if exp and case["expect_intent"] == "NORMAL":
            score_checks += 1
            if exp == "high" and spec is not None and spec >= 3:
                score_hits += 1
            elif exp == "low" and spec is not None and spec <= 2:
                score_hits += 1
            elif exp == "null" and spec is None:
                score_hits += 1
        exp_c = case.get("expect_correctness")
        if exp_c and case["expect_intent"] == "NORMAL":
            corr = ev.get("correctness") if ev else None
            corr_checks += 1
            if exp_c == "null" and corr is None:
                corr_hits += 1
            elif exp_c == "low" and corr is not None and corr <= 2:
                corr_hits += 1
            elif exp_c == "high" and corr is not None and corr >= 3:
                corr_hits += 1

    # 판별력: 같은 질문의 강한 답 vs 약한 답 구체성 점수 차
    def gap(strong: str, weak: str) -> float | None:
        a, b = med(spec_by_case.get(strong, [])), med(spec_by_case.get(weak, []))
        return round(a - b, 2) if a is not None and b is not None else None

    out.update(
        tag_compliance_pct=pct(tag_ok, len(ok)),
        intent_accuracy_pct=pct(intent_hit, len(ok)),
        intent_misses={
            cid: [i for i in v if i != F_BY_ID[cid]["expect_intent"]]
            for cid, v in per_case_intent.items()
            if any(i != F_BY_ID[cid]["expect_intent"] for i in v)
        },
        score_label_accuracy_pct=pct(score_hits, score_checks),
        correctness_rule_accuracy_pct=pct(corr_hits, corr_checks),
        discrimination_gap_backend=gap("f-strong-backend", "f-weak-vague"),
        discrimination_gap_personality=gap(
            "f-personality-star", "f-personality-rambling"
        ),
        question_len_median=med(lengths),
        question_len_le60_pct=pct(sum(1 for x in lengths if x <= 60), len(lengths)),
        non_korean_script_pct=pct(script_bad, len(ok)),
        latency_median=med([r["latency_sec"] for r in ok]),
        latency_p90=p90([r["latency_sec"] for r in ok]),
        ttft_median=med([r.get("ttft_sec") for r in ok]),
        ttft_p90=p90([r.get("ttft_sec") for r in ok]),
        over_3s_pct=pct(sum(1 for r in ok if r["latency_sec"] > 3), len(ok)),
        over_10s_timeout_pct=pct(sum(1 for r in ok if r["latency_sec"] > 10), len(ok)),
    )
    for tag in (":cold", ":after-cold", ":during-fanout"):
        rs = [r for r in recs if r["suite"] == "followup" + tag]
        if rs:
            out["latency" + tag.replace(":", "_").replace("-", "_")] = (
                rs[0]["latency_sec"] if rs[0]["ok"] else "ERR"
            )
    return out


def analyze_questions(recs: list[dict]) -> dict[str, Any]:
    main = [r for r in recs if r["suite"] == "questions"]
    ok = [r for r in main if r["ok"]]
    out: dict[str, Any] = {"calls": len(main), "success_pct": pct(len(ok), len(main))}
    errors = [r["error"][:160] for r in main if not r["ok"]]
    count_exact = mode_fit = job_ok = multi_ok = multi_checks = 0
    ev_required = ev_present = ev_grounded = ev_nonempty = 0
    dup_pairs = total_pairs = recent_dup = 0
    q_total = len_le80 = script_bad = md_bad = 0
    long_ok = []
    for r in ok:
        case = Q_BY_ID[r["case_id"]]
        qs = r["questions"]
        if len(qs) == case["max_questions"]:
            count_exact += 1
        cats = [q["category"] for q in qs]
        if case["mode"] == "PERSONALITY":
            mode_fit += cats.count("BEHAVIORAL") >= max(1, len(cats) // 2 + 1)
        elif case["mode"] == "TECHNICAL":
            mode_fit += cats.count("BEHAVIORAL") <= len(cats) // 3
        else:
            mode_fit += len(set(cats)) >= 3
        jobs = [q.get("job_category") for q in qs]
        job_ok += all(j in case["job_categories"] for j in jobs)
        if len(case["job_categories"]) > 1:
            multi_checks += 1
            multi_ok += all(j in jobs for j in case["job_categories"])
        for q in qs:
            q_total += 1
            text = q["question"]
            len_le80 += len(text) <= 80
            script_bad += script_issue(
                text + q.get("target_evidence", "") + q.get("expected_signal", "")
            )
            md_bad += bool(MARKDOWN.search(text))
            evid = q.get("target_evidence") or ""
            if q["category"] in ("PROJECT_DEEP_DIVE", "TECH_CHOICE"):
                ev_required += 1
                ev_present += bool(evid.strip())
            if evid.strip():
                ev_nonempty += 1
                ev_grounded += grounded(
                    evid, case["context"] + (case.get("self_introduction") or "")
                )
        for i in range(len(qs)):
            for j in range(i + 1, len(qs)):
                total_pairs += 1
                dup_pairs += jaccard(qs[i]["question"], qs[j]["question"]) >= 0.5
        for rq in case.get("recent_questions") or []:
            recent_dup += any(jaccard(rq, q["question"]) >= 0.5 for q in qs)
        if case["id"] == "q-long-context-p90":
            long_ok.append(len(qs) == case["max_questions"])
    out.update(
        errors=errors[:3],
        count_exact_pct=pct(count_exact, len(ok)),
        mode_category_fit_pct=pct(mode_fit, len(ok)),
        job_category_valid_pct=pct(job_ok, len(ok)),
        multi_job_coverage_pct=pct(multi_ok, multi_checks),
        evidence_present_when_required_pct=pct(ev_present, ev_required),
        evidence_grounded_pct=pct(ev_grounded, ev_nonempty),
        near_duplicate_pair_pct=pct(dup_pairs, total_pairs),
        repeated_recent_question_count=recent_dup,
        question_len_le80_pct=pct(len_le80, q_total),
        non_korean_script_pct=pct(script_bad, q_total),
        markdown_in_question_pct=pct(md_bad, q_total),
        long_context_success=f"{sum(long_ok)}/{len([r for r in main if r['case_id'] == 'q-long-context-p90'])}",
        latency_median=med([r["latency_sec"] for r in ok]),
        latency_p90=p90([r["latency_sec"] for r in ok]),
    )
    return out


def analyze_coaching(recs: list[dict]) -> dict[str, Any]:
    main = [r for r in recs if r["suite"] == "coaching"]
    ok = [r for r in main if r["ok"]]
    out: dict[str, Any] = {"calls": len(main), "success_pct": pct(len(ok), len(main))}
    invented = checks = script_bad = one_line = 0
    for r in ok:
        case = C_BY_ID[r["case_id"]]
        c = r["coaching"]
        body = (c.get("model_answer") or "") + (c.get("answer_rewrite") or "")
        source = case["answer"] + case["rag_context"] + case["question"]
        nums = {n for n in DIGITS.findall(body) if len(n) >= 2}
        checks += 1
        # 자료에 없는 2자리 이상 수치를 만들어 냈는가 (지어낸 실적 대리 지표)
        invented += any(n not in source for n in nums)
        script_bad += script_issue(body + (c.get("coaching_comment") or ""))
        cc = (c.get("coaching_comment") or "").strip()
        one_line += bool(cc) and "\n" not in cc and not MARKDOWN.search(cc)
    out.update(
        invented_numbers_pct=pct(invented, checks),
        non_korean_script_pct=pct(script_bad, len(ok)),
        comment_one_line_pct=pct(one_line, len(ok)),
        latency_median=med([r["latency_sec"] for r in ok]),
        latency_p90=p90([r["latency_sec"] for r in ok]),
    )
    fan = [r for r in recs if r["suite"] == "coaching:fanout15x5"]
    wall = [r for r in recs if r["suite"] == "fanout_wall"]
    if fan:
        out["fanout15x5_success"] = f"{sum(r['ok'] for r in fan)}/{len(fan)}"
        out["fanout15x5_wall_sec"] = wall[0]["wall_sec"] if wall else None
        out["fanout15x5_per_call_p90"] = p90([r["latency_sec"] for r in fan if r["ok"]])
    return out


def main() -> None:
    by_label: dict[str, list[dict]] = defaultdict(list)
    for path in sys.argv[1:]:
        with open(path, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line:
                    rec = json.loads(line)
                    by_label[rec["label"]].append(rec)
    summary = {}
    for label, recs in sorted(by_label.items()):
        summary[label] = {
            "followup": (
                analyze_followup(recs)
                if any(r["suite"].startswith("followup") for r in recs)
                else None
            ),
            "questions": (
                analyze_questions(recs)
                if any(r["suite"] == "questions" for r in recs)
                else None
            ),
            "coaching": (
                analyze_coaching(recs)
                if any(r["suite"].startswith("coaching") for r in recs)
                else None
            ),
        }
    print(json.dumps(summary, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
