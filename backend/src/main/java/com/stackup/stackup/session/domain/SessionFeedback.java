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

    @Column(name = "improvement_keywords", columnDefinition = "jsonb")
    private String improvementKeywords;

    @Column(name = "report_file_path", length = 1000)
    private String reportFilePath;
}
