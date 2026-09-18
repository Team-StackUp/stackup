"""3계열 판정자 패널 분석: 신뢰도(Krippendorff α)·계열 편향(혼합모형)·같은 계열 제외 패널 점수.

uv run --with numpy --with scipy --with pandas --with statsmodels --with krippendorff \
    python ai/scripts/llm_eval/judge_panel.py   (저장소 루트)
"""

from __future__ import annotations

import json

import krippendorff
import numpy as np
import pandas as pd
import statsmodels.formula.api as smf

ROUNDS = {
    "round1": [
        "docs/research/llm-eval-2026-09/eval-judge.jsonl",
        "docs/research/thesis/judges/gpt55-r1.jsonl",
    ],
    "round2": [
        "docs/research/local-llm-deep-dive-2026-09/data/judge.jsonl",
        "docs/research/thesis/judges/gpt55-r2.jsonl",
    ],
}
JUDGE_FAMILY = {
    "gemini-3.1-pro-preview": "google",
    "claude-opus-5": "anthropic",
    "gpt-5.5": "openai",
}


def cand_family(model: str) -> str:
    m = model.lower()
    if "gemini" in m or "gemma" in m:
        return "google"
    if "gpt-oss" in m or "gptoss" in m:
        return "openai"
    return "other"


rows = []
for rnd, paths in ROUNDS.items():
    for p in paths:
        for line in open(p, encoding="utf-8"):
            r = json.loads(line)
            for model, sc in (r.get("ratings") or {}).items():
                try:
                    rows.append(
                        dict(
                            round=rnd,
                            suite=r["suite"],
                            case=f"{rnd}:{r['suite']}:{r['case_id']}",
                            model=model,
                            judge=r["judge"],
                            score=float(sc["overall"]),
                        )
                    )
                except (KeyError, TypeError, ValueError):
                    pass
df = pd.DataFrame(rows)
df["same_family"] = [
    int(JUDGE_FAMILY[j] == cand_family(m)) for j, m in zip(df.judge, df.model)
]
df["item"] = df["case"] + "|" + df["model"]
print(
    "rows", len(df), "items", df["item"].nunique(), "judges", sorted(df.judge.unique())
)

# 1) 신뢰도: 3판정자 Krippendorff α(ordinal), 판정자 쌍별 Spearman
wide = df.pivot_table(index="item", columns="judge", values="score", aggfunc="mean")
wide3 = wide.dropna()
alpha = krippendorff.alpha(
    reliability_data=wide3.T.values, level_of_measurement="ordinal"
)
print(
    f"\n## 신뢰도 (완전 채점 {len(wide3)}문항)\nKrippendorff α (ordinal, 3 judges) = {alpha:.3f}"
)
js = list(wide3.columns)
for i in range(len(js)):
    for k in range(i + 1, len(js)):
        a, b = wide3[js[i]], wide3[js[k]]
        pa = krippendorff.alpha(
            reliability_data=np.vstack([a.values, b.values]),
            level_of_measurement="ordinal",
        )
        print(
            f"  {js[i]} vs {js[k]}: Spearman {a.corr(b, method='spearman'):.3f}, α {pa:.3f}, mean diff {np.mean(a - b):+.3f}"
        )

# 2) 계열 편향 혼합모형: score ~ C(model) + C(judge) + same_family + (1|case)
m = smf.mixedlm("score ~ C(model) + C(judge) + same_family", df, groups=df["case"]).fit(
    reml=True, method="lbfgs"
)
lo, hi = m.conf_int().loc["same_family"]
print(
    f"\n## 같은 계열 편향 (모델·판정자 고정효과 통제, 케이스 무작위 절편)\nsame_family = {m.params['same_family']:+.3f} [{lo:+.3f}, {hi:+.3f}], p = {m.pvalues['same_family']:.4g}"
)
# 판정자별 자기 계열 효과
for fam, judge in (("google", "gemini-3.1-pro-preview"), ("openai", "gpt-5.5")):
    sub = df.copy()
    sub["own"] = [
        int(j == judge and cand_family(mm) == fam)
        for j, mm in zip(sub.judge, sub.model)
    ]
    if sub["own"].sum() == 0:
        continue
    mm_ = smf.mixedlm("score ~ C(model) + C(judge) + own", sub, groups=sub["case"]).fit(
        reml=True, method="lbfgs"
    )
    lo2, hi2 = mm_.conf_int().loc["own"]
    print(
        f"  {judge} → {fam} 계열 후보: {mm_.params['own']:+.3f} [{lo2:+.3f}, {hi2:+.3f}] p={mm_.pvalues['own']:.4g} (n own={int(sub['own'].sum())})"
    )

# 3) 같은 계열 판정자 제외 패널 평균 (라운드·과제별)
df_loo = df[df.same_family == 0]
panel = (
    df_loo.groupby(["round", "suite", "model", "case"])["score"]
    .mean()
    .groupby(["round", "suite", "model"])
    .agg(["mean", "count"])
    .reset_index()
)
allj = (
    df.groupby(["round", "suite", "model", "case"])["score"]
    .mean()
    .groupby(["round", "suite", "model"])
    .mean()
    .rename("all_judges")
)
by_judge = (
    df.groupby(["round", "suite", "model", "judge"])["score"].mean().unstack("judge")
)
out = panel.merge(allj.reset_index(), on=["round", "suite", "model"]).merge(
    by_judge.reset_index(), on=["round", "suite", "model"]
)
out = out.rename(columns={"mean": "panel_excl_same_family", "count": "cases"})
pd.set_option("display.width", 250)
pd.set_option("display.max_columns", 20)
for (rnd, suite), g in out.groupby(["round", "suite"]):
    print(f"\n## {rnd} · {suite} (같은 계열 제외 패널 평균 내림차순)")
    print(
        g.drop(columns=["round", "suite"])
        .sort_values("panel_excl_same_family", ascending=False)
        .round(2)
        .to_string(index=False)
    )
out.round(3).to_csv("docs/research/thesis/judges/panel-scores.csv", index=False)
