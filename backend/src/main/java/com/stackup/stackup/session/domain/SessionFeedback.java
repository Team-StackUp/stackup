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

    @Column(name = "report_file_path", length = 1000)
    private String reportFilePath;

    // 공개 공유 토큰. null = 비공개. 공유 활성화 시 1회 발급(이후 유지).
    @Column(name = "share_token", length = 64, unique = true)
    private String shareToken;

    private SessionFeedback(InterviewSession session, Double overallScore, Double technicalAccuracy,
                            Double logicScore, Double communicationScore,
                            String strengthsSummary, String weaknessesSummary,
                            String improvementKeywordsJson, String reportFilePath) {
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
        this.reportFilePath = reportFilePath;
    }

    public static SessionFeedback of(InterviewSession session, Double overallScore,
                                     Double technicalAccuracy, Double logicScore,
                                     Double communicationScore,
                                     String strengthsSummary, String weaknessesSummary,
                                     String improvementKeywordsJson, String reportFilePath) {
        return new SessionFeedback(session, overallScore, technicalAccuracy, logicScore,
            communicationScore, strengthsSummary, weaknessesSummary,
            improvementKeywordsJson, reportFilePath);
    }

    // 공유 토큰을 보장(없으면 발급)하고 현재 토큰 반환. 멱등.
    public String enableShare(String token) {
        if (this.shareToken == null) {
            this.shareToken = token;
        }
        return this.shareToken;
    }
}
