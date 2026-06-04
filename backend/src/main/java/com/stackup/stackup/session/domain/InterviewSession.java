package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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

    // 대표 직군(다중 선택 시 첫 항목). 기존 표시/피드백/인덱스 하위호환용으로 유지.
    @Column(name = "job_category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private JobCategory jobCategory;

    // 직군 다중 선택. 한 세션이 여러 직군 질문을 함께 다룰 수 있다.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_job_categories", joinColumns = @JoinColumn(name = "session_id"))
    @Column(name = "job_category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<JobCategory> jobCategories = new LinkedHashSet<>();

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
                             List<JobCategory> jobCategories,
                             Integer maxQuestions, Integer maxDurationMinutes,
                             Integer generalQuestionCount, Integer maxFollowupsPerQuestion) {
        this.user = user;
        this.title = title;
        this.memo = memo;
        this.mode = mode;
        // 대표 직군 = 첫 선택, 전체 직군 = 중복 제거 후 보존.
        this.jobCategory = jobCategories.get(0);
        this.jobCategories = new LinkedHashSet<>(jobCategories);
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

    // 단일 직군 편의 오버로드 (테스트/기존 호출부 호환).
    public static InterviewSession create(User user, String title, String memo, SessionMode mode,
                                          JobCategory jobCategory,
                                          Integer maxQuestions, Integer maxDurationMinutes,
                                          Integer generalQuestionCount, Integer maxFollowupsPerQuestion) {
        if (jobCategory == null) {
            throw new IllegalArgumentException("jobCategory must not be null");
        }
        return create(user, title, memo, mode, List.of(jobCategory),
            maxQuestions, maxDurationMinutes, generalQuestionCount, maxFollowupsPerQuestion);
    }

    public static InterviewSession create(User user, String title, String memo, SessionMode mode,
                                          List<JobCategory> jobCategories,
                                          Integer maxQuestions, Integer maxDurationMinutes,
                                          Integer generalQuestionCount, Integer maxFollowupsPerQuestion) {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (jobCategories == null || jobCategories.isEmpty()) {
            throw new IllegalArgumentException("jobCategories must not be empty");
        }
        return new InterviewSession(user, title, memo, mode, jobCategories,
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
