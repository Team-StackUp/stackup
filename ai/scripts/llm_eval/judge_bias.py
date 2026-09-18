"""판정자 계열 편향 혼합효과 분석 (두 라운드 통합).

uv run --with numpy --with scipy --with pandas --with statsmodels python judge_bias.py
저장소 루트에서 실행.
"""

import json
from collections import defaultdict
import statsmodels.formula.api as smf
import pandas as pd

rows = []
for tag, path in [
    ("round1", "docs/research/llm-eval-2026-09/eval-judge.jsonl"),
    ("round2", "docs/research/local-llm-deep-dive-2026-09/data/judge.jsonl"),
]:
    sc = defaultdict(dict)
    for line in open(path):
        r = json.loads(line)
        for m, s in (r.get("ratings") or {}).items():
            try:
                sc[(r["suite"], r["case_id"], m)][r["judge"]] = float(s["overall"])
            except (KeyError, TypeError, ValueError):
                pass
    for (suite, cid, m), v in sc.items():
        if len(v) == 2:
            c, g = v["claude-opus-5"], v["gemini-3.1-pro-preview"]
            rows.append(
                dict(
                    round=tag,
                    suite=suite,
                    case=f"{tag}:{suite}:{cid}",
                    model=m,
                    claude=c,
                    gemini=g,
                    diff=g - c,
                    google=int(any(k in m.lower() for k in ("gemini", "gemma"))),
                    big_google=int(
                        m
                        in (
                            "gw-gemini-3.5-flash-lite",
                            "gw-gemini-3.1-pro",
                            "gw-gemma-4-31b",
                        )
                    ),
                )
            )
df = pd.DataFrame(rows)
df["quality"] = df["claude"]  # 다른 계열 판정자 점수를 품질 대리값으로
df["qc"] = df["quality"] - df["quality"].mean()
print("n pairs", len(df))
for f in [
    "diff ~ google",
    "diff ~ qc",
    "diff ~ google + qc",
    "diff ~ big_google + qc",
    "diff ~ google + qc + C(round)",
]:
    m = smf.mixedlm(f, df, groups=df["case"]).fit(reml=True, method="lbfgs")
    print(f"\n== {f}  (mixed model, random intercept per case)")
    for k in m.params.index:
        if k == "Group Var":
            continue
        lo, hi = m.conf_int().loc[k]
        print(
            f"  {k:28s} {m.params[k]:+.3f} [{lo:+.3f}, {hi:+.3f}] p={m.pvalues[k]:.4f}"
        )
# 판정자별 점수 분산(척도 사용 폭)
print(
    "\nscore SD: claude", round(df.claude.std(), 3), "gemini", round(df.gemini.std(), 3)
)
print(
    "slope gemini~claude (OLS):",
    smf.ols("gemini ~ claude", df).fit().params.round(3).to_dict(),
)
# 그룹별 평균 차
print(df.groupby(["round", "google"])["diff"].agg(["mean", "count"]).round(3))
