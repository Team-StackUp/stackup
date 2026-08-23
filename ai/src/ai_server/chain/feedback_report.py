"""피드백 마크다운 리포트 렌더러 (US-24 reportS3Key).

LLM 미호출 순수 함수 — 피드백 결과·답변 코칭·요청 메타데이터를 GFM 마크다운 문서로
조립한다. 저장 키는 storage.md §2 의 `feedback/{session_id}/report.md`, 소비는 Core 프록시
경유 프론트 Markdown 뷰어.

포맷 주의: 요약·패널 detail 등은 plain text 계약 필드(마크다운 기호 없음 보장)라 골격에
그대로 삽입하고, modelAnswer/answerRewrite 는 원래 GFM 계약 필드라 원문 그대로 싣는다.
사용자 답변 원문은 싣지 않는다 — 사용자 입력 텍스트는 마크다운으로 렌더하지 않는 규칙.
"""

from __future__ import annotations

from ai_server.chain.feedback_generation_chain import _DOMAIN_KO, FeedbackResult
from ai_server.model.messages.feedback import (
    AnswerCoachingItem,
    FeedbackMessageItem,
    GenerateFeedbackRequest,
    PanelBreakdownItem,
    VoiceAnalysisSummary,
)

_MODE_LABEL = {
    "PERSONALITY": "인성 면접",
    "TECHNICAL": "기술 면접",
    "INTEGRATED": "통합 면접",
    "JOB_TAILORED": "직무 맞춤 면접",
}

_END_REASON_LABEL = {
    "USER_REQUEST": "사용자 종료",
    "MAX_QUESTIONS_REACHED": "질문 수 도달",
    "POOL_EXHAUSTED": "질문 소진",
    "DURATION_EXCEEDED": "시간 초과 자동 종료",
}


def _fmt_score(value: float | None) -> str:
    if value is None:
        return "—"
    # 80.0 → "80", 82.5 → "82.5" — 표에서 소수점 노이즈 제거.
    return f"{value:g}"


def _section(lines: list[str], title: str) -> None:
    lines.append("")
    lines.append(f"## {title}")
    lines.append("")


def _render_scores(lines: list[str], result: FeedbackResult) -> None:
    _section(lines, "종합 점수")
    lines.append("| 항목 | 점수 |")
    lines.append("| --- | ---: |")
    lines.append(f"| 종합 | {_fmt_score(result.overall_score)} |")
    lines.append(f"| 기술 정확도 | {_fmt_score(result.technical_accuracy)} |")
    lines.append(f"| 논리 | {_fmt_score(result.logic_score)} |")
    lines.append(f"| 전달력 | {_fmt_score(result.communication_score)} |")


def _render_panel(lines: list[str], panel: list[PanelBreakdownItem]) -> None:
    if not panel:
        return
    _section(lines, "평가위원 패널")
    for item in panel:
        head = f"### {item.evaluator} — {item.dimension}"
        if item.score is not None:
            head += f" ({_fmt_score(item.score)}점)"
        lines.append(head)
        lines.append("")
        if item.strength:
            lines.append(f"- **강점**: {item.strength}")
        if item.weakness:
            lines.append(f"- **약점**: {item.weakness}")
        if item.strength or item.weakness:
            lines.append("")
        if item.detail:
            lines.append(item.detail)
            lines.append("")
        if item.score_rationale:
            lines.append(f"> 점수 근거: {item.score_rationale}")
            lines.append("")


def _render_coaching(
    lines: list[str],
    coaching: list[AnswerCoachingItem],
    messages: list[FeedbackMessageItem],
) -> None:
    if not coaching:
        return
    _section(lines, "답변별 코칭")
    by_id = {m.id: m for m in messages}
    # 번호는 전사 내 질문(INTERVIEWER) 순서 기준 — 코칭 일부가 실패해 목록에 구멍이 나도
    # 같은 질문은 재생성마다 같은 번호를 갖는다(enumerate 는 번호가 밀린다).
    interviewers = sorted(
        (m for m in messages if m.role == "INTERVIEWER"),
        key=lambda m: m.sequence_number,
    )
    question_no = {m.id: i for i, m in enumerate(interviewers, start=1)}
    for item in coaching:
        question = _question_for(item.message_id, by_id)
        if question is not None:
            no = question_no.get(question.id)
            label = f"Q{no}" if no is not None else "Q"
            # 다행 질문은 한 줄로 접는다 — ATX 헤딩은 첫 개행에서 끊긴다.
            lines.append(f"### {label}. {' '.join(question.content.split())}")
        else:
            lines.append("### Q. (질문 미확인)")
        lines.append("")
        if item.coaching_comment:
            lines.append(f"**코칭 한 줄**: {item.coaching_comment}")
            lines.append("")
        if item.model_answer:
            lines.append("#### 모범 답안")
            lines.append("")
            lines.append(item.model_answer)
            lines.append("")
        if item.answer_rewrite:
            lines.append("#### 내 답변, 이렇게 고치면")
            lines.append("")
            lines.append(item.answer_rewrite)
            lines.append("")


def _question_for(
    message_id: int, by_id: dict[int, FeedbackMessageItem]
) -> FeedbackMessageItem | None:
    answer = by_id.get(message_id)
    if answer is None or answer.parent_message_id is None:
        return None
    question = by_id.get(answer.parent_message_id)
    if question is None or question.role != "INTERVIEWER":
        return None
    return question


def _render_voice(lines: list[str], voice: VoiceAnalysisSummary | None) -> None:
    if voice is None:
        return
    rows: list[str] = []
    if voice.analyzed_message_count is not None:
        rows.append(f"- 분석된 답변 수: {voice.analyzed_message_count}")
    if voice.average_speaking_rate_wpm is not None:
        rows.append(f"- 평균 말속도: {voice.average_speaking_rate_wpm:.0f} WPM")
    if voice.total_silence_duration_sec is not None:
        rows.append(f"- 총 무음 시간: {voice.total_silence_duration_sec:.0f}초")
    fillers = [(w, c) for w, c in (voice.filler_word_counts or {}).items() if c > 0]
    if fillers:
        joined = " · ".join(f"{w} {c}회" for w, c in fillers)
        rows.append(f"- 간투어: {joined}")
    if not rows:
        return
    _section(lines, "음성 전달력 요약")
    lines.extend(rows)


def render_feedback_report(
    *,
    req: GenerateFeedbackRequest,
    result: FeedbackResult,
    answer_coaching: list[AnswerCoachingItem],
) -> str:
    """FeedbackCallbackPayload 조립 직전 시점의 결과물로 리포트 마크다운을 만든다."""
    lines: list[str] = ["# 면접 피드백 리포트", ""]

    mode = _MODE_LABEL.get(req.mode, req.mode)
    job = _DOMAIN_KO.get(req.job_category, req.job_category)
    lines.append(f"- 세션: {req.session_id}")
    lines.append(f"- 면접 유형: {mode} · {job}")
    if req.total_question_count is not None:
        lines.append(f"- 질문 수: {req.total_question_count}")
    if req.end_reason:
        lines.append(
            f"- 종료 사유: {_END_REASON_LABEL.get(req.end_reason, req.end_reason)}"
        )
    if req.target_company_name:
        # 사용자 입력 — 개행을 접어 마크다운 구조 주입('\n## …')을 차단한다.
        lines.append(f"- 지원 회사: {' '.join(req.target_company_name.split())}")

    _render_scores(lines, result)
    _render_panel(lines, result.panel_breakdown)

    if result.strengths_summary:
        _section(lines, "강점 요약")
        lines.append(result.strengths_summary)
    if result.weaknesses_summary:
        _section(lines, "개선점 요약")
        lines.append(result.weaknesses_summary)
    if result.improvement_keywords:
        _section(lines, "개선 키워드")
        lines.extend(f"- {kw}" for kw in result.improvement_keywords)
    if result.study_plan:
        _section(lines, "학습 플랜")
        lines.extend(
            f"{i}. {step}" for i, step in enumerate(result.study_plan, start=1)
        )
    if result.highlights:
        _section(lines, "핵심 하이라이트")
        lines.extend(f"> {h}" for h in result.highlights)

    _render_coaching(lines, answer_coaching, req.messages)
    _render_voice(lines, req.voice_analysis_summary)

    return "\n".join(lines).rstrip() + "\n"
