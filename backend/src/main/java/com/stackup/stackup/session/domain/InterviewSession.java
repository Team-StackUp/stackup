package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.time.Instant;

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
    @Enumerated(EnumType.STRING)
    private SessionMode mode;

    @Column(name = "interview_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private InterviewType interviewType;

    @Column(name = "job_category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private JobCategory jobCategory;

    @Column(name = "max_questions", nullable = false)
    private Integer maxQuestions = 10;

    @Column(name = "max_duration_minutes", nullable = false)
    private Integer maxDurationMinutes = 60;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.READY;

    @Column(name = "total_question_count")
    private Integer totalQuestionCount = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public static InterviewSession create(
            User user, String title, String memo,
            SessionMode mode, InterviewType interviewType, JobCategory jobCategory,
            Integer maxQuestions, Integer maxDurationMinutes
    ) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (interviewType == null) {
            throw new IllegalArgumentException("interviewType must not be null");
        }
        if (jobCategory == null) {
            throw new IllegalArgumentException("jobCategory must not be null");
        }
        InterviewSession s = new InterviewSession();
        s.user = user;
        s.title = title;
        s.memo = memo;
        s.mode = mode;
        s.interviewType = interviewType;
        s.jobCategory = jobCategory;
        s.maxQuestions = maxQuestions == null ? 10 : maxQuestions;
        s.maxDurationMinutes = maxDurationMinutes == null ? 60 : maxDurationMinutes;
        s.status = SessionStatus.READY;
        s.totalQuestionCount = 0;
        return s;
    }

    public void markInProgress() {
        if (this.status != SessionStatus.READY) {
            throw new IllegalStateException("IN_PROGRESS 전이는 READY 상태에서만 가능합니다. 현재=" + this.status);
        }
        this.status = SessionStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void incrementQuestionCount() {
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("IN_PROGRESS 상태에서만 증가할 수 있습니다.");
        }
        this.totalQuestionCount = (this.totalQuestionCount == null ? 0 : this.totalQuestionCount) + 1;
    }

    public boolean isMaxReached() {
        return this.totalQuestionCount != null
                && this.maxQuestions != null
                && this.totalQuestionCount >= this.maxQuestions;
    }

    public void end() {
        if (this.status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("IN_PROGRESS 상태에서만 종료할 수 있습니다. 현재=" + this.status);
        }
        this.status = SessionStatus.COMPLETED;
        this.endedAt = Instant.now();
    }

    public void cancel() {
        if (this.status == SessionStatus.COMPLETED || this.status == SessionStatus.CANCELLED) {
            throw new IllegalStateException("취소할 수 없는 상태입니다. 현재=" + this.status);
        }
        this.status = SessionStatus.CANCELLED;
        this.endedAt = Instant.now();
    }
}
