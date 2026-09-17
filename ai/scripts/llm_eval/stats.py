"""판정·자동지표·지연 데이터의 통계 분석 (논문용).

의존성: numpy, scipy (프로젝트 의존성에 넣지 않음)
    uv run --with numpy --with scipy python stats.py \
        --judge docs/research/local-llm-deep-dive-2026-09/data/judge.jsonl \
        --raw-dir docs/research/local-llm-deep-dive-2026-09/data/raw \
        --baseline gw-gemini-3.5-flash-lite --out stats.md

분석
1. 모델별 품질 점수: 판정자별 평균 + 케이스 단위 클러스터 부트스트랩 95% CI
2. 기준 모델 대비 쌍대 비교: 케이스별 (두 판정자 평균) 점수 차 → Wilcoxon signed-rank,
   rank-biserial 효과크기, Holm 보정
3. 판정자 신뢰도: Spearman ρ, 2차 가중 Cohen's κ, ICC(2,1), ±1 이내 일치율
4. 판정자 계열 편향: (Gemini 판정 − Claude 판정) 을 Google 계열 모델 vs 그 외로 비교,
   차이의 부트스트랩 CI + Mann-Whitney U
5. 검정력: 관측된 쌍대 차이 표준편차로 0.3 / 0.5 점 차이를 검출하는 데 필요한 케이스 수
6. 자동 지표 비율의 Wilson 95% CI, 지연 중간값의 부트스트랩 95% CI
"""

from __future__ import annotations

import argparse
import glob
import json
import math
import os
from collections import defaultdict

import numpy as np
from scipy import stats

RNG = np.random.default_rng(20260917)
GOOGLE_FAMILY = ("gemini", "gemma")
JUDGES = ("claude-opus-5", "gemini-3.1-pro-preview")


def boot_ci(values_by_cluster: list[list[float]], n: int = 5000, stat=np.mean):
    """클러스터(케이스) 단위 재표집 부트스트랩."""
    clusters = [np.asarray(v, dtype=float) for v in values_by_cluster if len(v)]
    if not clusters:
        return (float("nan"), float("nan"), float("nan"))
    point = stat(np.concatenate(clusters))
    k = len(clusters)
    boots = np.empty(n)
    for i in range(n):
        idx = RNG.integers(0, k, k)
        boots[i] = stat(np.concatenate([clusters[j] for j in idx]))
    lo, hi = np.percentile(boots, [2.5, 97.5])
    return (float(point), float(lo), float(hi))


def wilson(k: int, n: int, z: float = 1.96):
    if n == 0:
        return (float("nan"),) * 3
    p = k / n
    den = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / den
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / den
    return (p, max(0.0, centre - half), min(1.0, centre + half))


def holm(pvals: dict[str, float]) -> dict[str, float]:
    items = sorted(pvals.items(), key=lambda kv: kv[1])
    m = len(items)
    adj, running = {}, 0.0
    for i, (k, p) in enumerate(items):
        running = max(running, min(1.0, (m - i) * p))
        adj[k] = running
    return adj


def quadratic_kappa(a: list[int], b: list[int], lo: int = 1, hi: int = 5) -> float:
    cats = hi - lo + 1
    obs = np.zeros((cats, cats))
    for x, y in zip(a, b):
        obs[x - lo, y - lo] += 1
    W = np.array(
        [[(i - j) ** 2 / (cats - 1) ** 2 for j in range(cats)] for i in range(cats)]
    )
    E = np.outer(obs.sum(1), obs.sum(0)) / obs.sum()
    return float(1 - (W * obs).sum() / (W * E).sum())


def icc21(mat: np.ndarray) -> float:
    """ICC(2,1): two-way random, absolute agreement, single rater. mat: n_items × k_raters."""
    n, k = mat.shape
    grand = mat.mean()
    ms_r = k * ((mat.mean(1) - grand) ** 2).sum() / (n - 1)
    ms_c = n * ((mat.mean(0) - grand) ** 2).sum() / (k - 1)
    ss_e = (
        (mat - mat.mean(1, keepdims=True) - mat.mean(0, keepdims=True) + grand) ** 2
    ).sum()
    ms_e = ss_e / ((n - 1) * (k - 1))
    return float((ms_r - ms_e) / (ms_r + (k - 1) * ms_e + k * (ms_c - ms_e) / n))


def n_for_paired(
    sd: float, delta: float, alpha: float = 0.05, power: float = 0.8
) -> int:
    za, zb = stats.norm.ppf(1 - alpha / 2), stats.norm.ppf(power)
    return int(math.ceil(((za + zb) * sd / delta) ** 2)) if delta > 0 and sd > 0 else 0


def load_judge(path: str):
    """→ scores[suite][model][case][judge] = overall"""
    scores = defaultdict(lambda: defaultdict(lambda: defaultdict(dict)))
    for line in open(path, encoding="utf-8"):
        r = json.loads(line)
        for model, sc in (r.get("ratings") or {}).items():
            try:
                scores[r["suite"]][model][r["case_id"]][r["judge"]] = int(
                    round(float(sc["overall"]))
                )
            except (KeyError, TypeError, ValueError):
                continue
    return scores


def fmt(t, d=2):
    return f"{t[0]:.{d}f} [{t[1]:.{d}f}, {t[2]:.{d}f}]"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--judge", required=True)
    ap.add_argument("--raw-dir", default="")
    ap.add_argument("--baseline", default="gw-gemini-3.5-flash-lite")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    scores = load_judge(args.judge)
    out: list[str] = []
    w = out.append
    w(
        f"# 통계 분석 — `{os.path.basename(os.path.dirname(os.path.dirname(args.judge)) or args.judge)}`\n"
    )
    w(
        "부트스트랩: 케이스 단위 클러스터 재표집 5,000회, 95% 백분위 구간. 난수 시드 20260917.\n"
    )

    # 1) 품질 점수 + CI
    agree_pairs = []  # (claude, gemini, model)
    pair_results = {}
    for suite in ("followup", "questions", "coaching"):
        models = scores.get(suite, {})
        if not models:
            continue
        w(f"\n## 1. 품질 점수 — {suite}\n")
        w(
            "| 모델 | 케이스 | Claude 평균 [95% CI] | Gemini 평균 [95% CI] | 두 판정자 평균 [95% CI] |"
        )
        w("|---|---|---|---|---|")
        rows = []
        for model, cases in models.items():
            by_case_c = [
                [v["claude-opus-5"]] for v in cases.values() if "claude-opus-5" in v
            ]
            by_case_g = [
                [v["gemini-3.1-pro-preview"]]
                for v in cases.values()
                if "gemini-3.1-pro-preview" in v
            ]
            by_case_m = [
                [np.mean([v[j] for j in JUDGES if j in v])] for v in cases.values() if v
            ]
            c, g, m = boot_ci(by_case_c), boot_ci(by_case_g), boot_ci(by_case_m)
            rows.append((c[0], model, len(cases), c, g, m))
            for v in cases.values():
                if all(j in v for j in JUDGES):
                    agree_pairs.append(
                        (v["claude-opus-5"], v["gemini-3.1-pro-preview"], model)
                    )
        for _, model, n, c, g, m in sorted(rows, reverse=True):
            w(f"| {model} | {n} | {fmt(c)} | {fmt(g)} | {fmt(m)} |")

        # 2) 기준 대비 쌍대 비교
        base = models.get(args.baseline)
        if not base:
            continue
        w(f"\n### 2. `{args.baseline}` 대비 쌍대 비교 — {suite}\n")
        w(
            "케이스별 두 판정자 평균 점수의 차(모델 − 기준). Wilcoxon signed-rank (zero_method=zsplit), 효과크기 rank-biserial r, Holm 보정.\n"
        )
        raw_p, rows2 = {}, []
        for model, cases in models.items():
            if model == args.baseline:
                continue
            common = [cid for cid in cases if cid in base]
            if len(common) < 3:
                continue
            d = np.array(
                [
                    np.mean([cases[c][j] for j in JUDGES if j in cases[c]])
                    - np.mean([base[c][j] for j in JUDGES if j in base[c]])
                    for c in common
                ]
            )
            if np.allclose(d, 0):
                p, r = 1.0, 0.0
            else:
                res = stats.wilcoxon(d, zero_method="zsplit")
                p = float(res.pvalue)
                ranks = stats.rankdata(np.abs(d))
                r = float((ranks[d > 0].sum() - ranks[d < 0].sum()) / ranks.sum())
            ci = boot_ci([[x] for x in d])
            raw_p[model] = p
            rows2.append(
                (
                    model,
                    len(common),
                    ci,
                    float(np.std(d, ddof=1)) if len(d) > 1 else 0.0,
                    p,
                    r,
                )
            )
            pair_results[(suite, model)] = (
                ci,
                p,
                r,
                float(np.std(d, ddof=1)) if len(d) > 1 else 0.0,
                len(common),
            )
        adj = holm(raw_p)
        w("| 모델 | n | 평균 차 [95% CI] | 차이 SD | p | Holm p | r |")
        w("|---|---|---|---|---|---|---|")
        for model, n, ci, sd, p, r in sorted(
            rows2, key=lambda x: x[2][0], reverse=True
        ):
            w(
                f"| {model} | {n} | {fmt(ci)} | {sd:.2f} | {p:.4f} | {adj[model]:.4f} | {r:+.2f} |"
            )

    # 3) 판정자 신뢰도
    w("\n## 3. 판정자 간 신뢰도 (모든 과제·모델·케이스 overall)\n")
    a = [x[0] for x in agree_pairs]
    b = [x[1] for x in agree_pairs]
    rho = stats.spearmanr(a, b)
    within1 = np.mean(np.abs(np.array(a) - np.array(b)) <= 1)
    exact = np.mean(np.array(a) == np.array(b))
    w("| 지표 | 값 |\n|---|---|")
    w(f"| 쌍 수 | {len(a)} |")
    w(f"| 완전 일치 | {exact:.1%} |")
    w(f"| ±1 이내 일치 | {within1:.1%} |")
    w(f"| Spearman ρ | {rho.statistic:.3f} (p={rho.pvalue:.2e}) |")
    w(f"| 2차 가중 Cohen's κ | {quadratic_kappa(a, b):.3f} |")
    w(f"| ICC(2,1) 절대 일치 | {icc21(np.array([a, b], dtype=float).T):.3f} |")
    w(f"| 평균 (Claude − Gemini) | {np.mean(np.array(a) - np.array(b)):+.3f} |")

    # 4) 계열 편향
    w("\n## 4. 판정자 계열 편향 (Gemini 판정 − Claude 판정)\n")
    diff_g = [
        g - c for c, g, m in agree_pairs if any(f in m.lower() for f in GOOGLE_FAMILY)
    ]
    diff_o = [
        g - c
        for c, g, m in agree_pairs
        if not any(f in m.lower() for f in GOOGLE_FAMILY)
    ]
    ci_g, ci_o = boot_ci([[x] for x in diff_g]), boot_ci([[x] for x in diff_o])
    dd = [
        np.mean(RNG.choice(diff_g, len(diff_g)))
        - np.mean(RNG.choice(diff_o, len(diff_o)))
        for _ in range(5000)
    ]
    mw = stats.mannwhitneyu(diff_g, diff_o, alternative="two-sided")
    w("| 대상 | 쌍 수 | 평균 차 [95% CI] |\n|---|---|---|")
    w(f"| Google 계열 (Gemini·Gemma) | {len(diff_g)} | {fmt(ci_g)} |")
    w(f"| 그 외 | {len(diff_o)} | {fmt(ci_o)} |")
    lo, hi = np.percentile(dd, [2.5, 97.5])
    w(
        f"| **계열 편향 추정 (차이의 차)** | — | **{np.mean(diff_g) - np.mean(diff_o):+.2f} [{lo:+.2f}, {hi:+.2f}]** |"
    )
    w(
        f"\nMann-Whitney U={mw.statistic:.0f}, p={mw.pvalue:.4f}. 쌍은 같은 케이스 내에서 독립이 아니므로 p 값은 참고용이다.\n"
    )

    # 5) 검정력
    w("\n## 5. 필요한 케이스 수 (쌍대 비교, α=0.05 양측, 검정력 0.8, 정규 근사)\n")
    w("| 과제 | 관측 차이 SD (중간값) | 0.3점 검출 | 0.5점 검출 | 1.0점 검출 |")
    w("|---|---|---|---|---|")
    for suite in ("followup", "questions", "coaching"):
        sds = [v[3] for (s, _), v in pair_results.items() if s == suite and v[3] > 0]
        if not sds:
            continue
        sd = float(np.median(sds))
        w(
            f"| {suite} | {sd:.2f} | {n_for_paired(sd, 0.3)} | {n_for_paired(sd, 0.5)} | {n_for_paired(sd, 1.0)} |"
        )

    # 6) 자동 지표 비율 CI + 지연 CI
    if args.raw_dir:
        sys_path = os.path.dirname(os.path.abspath(__file__))
        import sys

        sys.path.insert(0, sys_path)
        import importlib

        FOLLOWUP_CASES = importlib.import_module(
            os.environ.get("LLM_EVAL_CASES", "cases")
        ).FOLLOWUP_CASES

        f_by = {c["id"]: c for c in FOLLOWUP_CASES}
        w("\n## 6. 자동 지표 비율의 Wilson 95% CI (꼬리질문)\n")
        w(
            "| 모델 | 의도 분류 정확도 | 사실대조 규칙 준수 | 꼬리질문 지연 중간값 [95% CI] |"
        )
        w("|---|---|---|---|")
        for path in sorted(glob.glob(os.path.join(args.raw_dir, "*.jsonl"))):
            if "INVALID" in path or os.path.basename(path).startswith("load-"):
                continue
            recs = [
                json.loads(line)
                for line in open(path, encoding="utf-8")
                if line.strip()
            ]
            fu = [r for r in recs if r.get("suite") == "followup" and r.get("ok")]
            if not fu:
                continue
            hit = sum(
                r.get("answer_intent") == f_by[r["case_id"]]["expect_intent"]
                for r in fu
            )
            corr_n = corr_k = 0
            for r in fu:
                case = f_by[r["case_id"]]
                exp = case.get("expect_correctness")
                if not exp or case["expect_intent"] != "NORMAL":
                    continue
                corr_n += 1
                c = (r.get("answer_evaluation") or {}).get("correctness")
                corr_k += (
                    (exp == "null" and c is None)
                    or (exp == "low" and c is not None and c <= 2)
                    or (exp == "high" and c is not None and c >= 3)
                )
            by_case = defaultdict(list)
            for r in fu:
                by_case[r["case_id"]].append(r["latency_sec"])
            lat = boot_ci(list(by_case.values()), stat=np.median)
            iw, cw = wilson(hit, len(fu)), wilson(corr_k, corr_n)
            w(
                f"| {recs[0]['label']} | {iw[0]:.1%} [{iw[1]:.1%}, {iw[2]:.1%}] (n={len(fu)}) | "
                f"{cw[0]:.1%} [{cw[1]:.1%}, {cw[2]:.1%}] (n={corr_n}) | {fmt(lat)}s |"
            )

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(out) + "\n")
    print("\n".join(out))


if __name__ == "__main__":
    main()
