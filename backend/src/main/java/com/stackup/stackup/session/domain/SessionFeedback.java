package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "session_feedbacks")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionFeedback extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private InterviewSession session;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "technical_accuracy")
    private Double technicalAccuracy;

    @Column(name = "logic_score")
    private Double logicScore;

    @Column(name = "communication_score")
    private Double communicationScore;

    @Column(name = "strengths_summary", columnDefinition = "text")
    private String strengthsSummary;

    @Column(name = "weaknesses_summary", columnDefinition = "text")
    private String weaknessesSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_keywords", columnDefinition = "jsonb")
    private String improvementKeywords;

    // 멀티 면접관 패널의 평가위원별 분해 JSON. null = 단일/레거시.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "panel_breakdown", columnDefinition = "jsonb")
    private String panelBreakdown;

    // 학습 방향/다음 단계 액션 아이템 JSON 배열.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "study_plan", columnDefinition = "jsonb")
    private String studyPlan;

    // 강조 표시용 핵심 구절 JSON 배열(강점·개선 본문에서 발췌). 프론트가 부분 문자열 매칭해 하이라이트.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "highlights", columnDefinition = "jsonb")
    private String highlights;

    @Column(name = "report_file_path", length = 1000)
    private String reportFilePath;

    // 공개 공유 토큰. null = 비공개. 공유 활성화 시 1회 발급(이후 유지).
    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    private SessionFeedback(InterviewSession session, Double overallScore, Double technicalAccuracy,
                            Double logicScore, Double communicationScore,
                            String strengthsSummary, String weaknessesSummary,
                            String improvementKeywordsJson, String panelBreakdownJson,
                            String studyPlanJson, String highlightsJson, String reportFilePath) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        this.session = session;
        this.overallScore = overallScore;
        this.technicalAccuracy = technicalAccuracy;
        this.logicScore = logicScore;
        this.communicationScore = communicationScore;
        this.strengthsSummary = strengthsSummary;
        this.weaknessesSummary = weaknessesSummary;
        this.improvementKeywords = improvementKeywordsJson;
        this.panelBreakdown = panelBreakdownJson;
        this.studyPlan = studyPlanJson;
        this.highlights = highlightsJson;
        this.reportFilePath = reportFilePath;
    }

    public static SessionFeedback of(InterviewSession session, Double overallScore,
                                     Double technicalAccuracy, Double logicScore,
                                     Double communicationScore,
                                     String strengthsSummary, String weaknessesSummary,
                                     String improvementKeywordsJson, String panelBreakdownJson,
                                     String studyPlanJson, String highlightsJson,
                                     String reportFilePath) {
        return new SessionFeedback(session, overallScore, technicalAccuracy, logicScore,
            communicationScore, strengthsSummary, weaknessesSummary,
            improvementKeywordsJson, panelBreakdownJson, studyPlanJson, highlightsJson,
            reportFilePath);
    }

    // 공유 토큰을 보장(없으면 발급)하고 현재 토큰 반환. 멱등.
    public String enableShare(String token) {
        if (this.shareToken == null) {
            this.shareToken = token;
        }
        return this.shareToken;
    }

    // 공유 해제. 토큰을 지우면 기존 공유 링크는 즉시 404 가 된다. 멱등.
    public void disableShare() {
        this.shareToken = null;
    }
}
