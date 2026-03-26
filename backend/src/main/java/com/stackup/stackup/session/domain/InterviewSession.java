package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "interview_sessions",
        indexes = {
                @Index(name = "idx_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_sessions_user_status", columnList = "user_id, status")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewSession extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String memo;

    @Column(nullable = false, length = 20)
    private String mode;

    @Column(name = "interview_type", nullable = false, length = 30)
    private String interviewType;

    @Column(name = "job_category", nullable = false, length = 30)
    private String jobCategory;

    @Column(name = "max_questions", nullable = false)
    private Integer maxQuestions = 10;

    @Column(name = "max_duration_minutes", nullable = false)
    private Integer maxDurationMinutes = 60;

    @Column(nullable = false, length = 20)
    private String status = "READY";

    @Column(name = "total_question_count")
    private Integer totalQuestionCount = 0;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;
}
