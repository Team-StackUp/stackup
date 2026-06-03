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

    @Column(name = "job_category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private JobCategory jobCategory;

    @Column(name = "max_questions", nullable = false)
    private Integer maxQuestions = 10;

    @Column(name = "max_duration_minutes", nullable = false)
    private Integer maxDurationMinutes = 60;

    // 일반질문 수(n). 서로 다른 주제로 풀에서 꺼내 묻는다.
    @Column(name = "general_question_count", nullable = false)
    private Integer generalQuestionCount = 3;

    // 일반질문 1개당 최대 꼬리질문 수(m).
    @Column(name = "max_followups_per_question", nullable = false)
    private Integer maxFollowupsPerQuestion = 2;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.READY;

    @Column(name = "total_question_count")
    private Integer totalQuestionCount = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    private InterviewSession(User user, String title, String memo, SessionMode mode,
                             JobCategory jobCategory,
                             Integer maxQuestions, Integer maxDurationMinutes,
                             Integer generalQuestionCount, Integer maxFollowupsPerQuestion) {
        this.user = user;
        this.title = title;
        this.memo = memo;
        this.mode = mode;
        this.jobCategory = jobCategory;
        if (maxQuestions != null) {
            this.maxQuestions = maxQuestions;
        }
        if (maxDurationMinutes != null) {
            this.maxDurationMinutes = maxDurationMinutes;
        }
        if (generalQuestionCount != null) {
            this.generalQuestionCount = generalQuestionCount;
        }
        if (maxFollowupsPerQuestion != null) {
            this.maxFollowupsPerQuestion = maxFollowupsPerQuestion;
        }
    }

    public static InterviewSession create(User user, String title, String memo, SessionMode mode,
                                          JobCategory jobCategory,
                                          Integer maxQuestions, Integer maxDurationMinutes,
                                          Integer generalQuestionCount, Integer maxFollowupsPerQuestion) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (mode == null || jobCategory == null) {
            throw new IllegalArgumentException("mode/jobCategory must not be null");
        }
        return new InterviewSession(user, title, memo, mode, jobCategory,
            maxQuestions, maxDurationMinutes, generalQuestionCount, maxFollowupsPerQuestion);
    }

    public void start() {
        if (status != SessionStatus.READY) {
            throw new IllegalStateException("session is not READY to start (current=" + status + ")");
        }
        this.status = SessionStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    public void end() {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("session is not IN_PROGRESS to end (current=" + status + ")");
        }
        this.status = SessionStatus.COMPLETED;
        this.endedAt = Instant.now();
    }

    public void interrupt() {
        if (status != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("session is not IN_PROGRESS to interrupt (current=" + status + ")");
        }
        this.status = SessionStatus.INTERRUPTED;
        this.endedAt = Instant.now();
    }

    public void cancel() {
        if (status != SessionStatus.READY) {
            throw new IllegalStateException("only READY session can be cancelled (current=" + status + ")");
        }
        this.status = SessionStatus.CANCELLED;
    }

    public void incrementQuestionCount() {
        if (totalQuestionCount == null) {
            totalQuestionCount = 0;
        }
        totalQuestionCount++;
    }

    public boolean isMaxReached() {
        return totalQuestionCount != null
                && maxQuestions != null
                && totalQuestionCount >= maxQuestions;
    }

    public void updateTitleAndMemo(String title, String memo) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        if (memo != null) {
            this.memo = memo;
        }
    }
}
