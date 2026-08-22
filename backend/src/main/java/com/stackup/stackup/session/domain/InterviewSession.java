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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
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

    // 직무 맞춤(JOB_TAILORED) 모드 전용: 지원 회사명 + 채용공고(JD) 원문. 다른 모드는 null.
    @Column(name = "target_company_name", length = 200)
    private String targetCompanyName;

    @Column(name = "target_job_description", columnDefinition = "text")
    private String targetJobDescription;

    // 약점 집중 재도전에서 겨냥할 평가 축. SessionFocusArea enum name 의 JSON 배열
    // (예: ["LOGIC","COMMUNICATION"]). 지정 없으면 null — 일반 면접과 동일하게 동작한다.
    // 직렬화/역직렬화는 application 레이어가 한다(SessionFeedback.improvementKeywords 와 같은 방식).
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "focus_areas", columnDefinition = "jsonb")
    private String focusAreas;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.READY;

    @Column(name = "total_question_count")
    private Integer totalQuestionCount = 0;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    // 중단 후 이어하기로 재개한 시각. 시간 한도는 이 값 기준으로 다시 잰다(없으면 startedAt).
    // startedAt 은 '처음 시작한 시각'으로 보존한다.
    @Column(name = "resumed_at")
    private Instant resumedAt;

    // 피드백 생성 실패 마커(V29). SSE ERROR 는 휘발성이라 그 순간 미접속 클라이언트가
    // "생성 중"과 "실패"를 구분할 수 없다 — FAILED 콜백 수신 시 기록하고
    // 성공 콜백·재생성 요청 시 클리어해 GET 피드백이 실패를 즉시 알리게 한다.
    @Column(name = "feedback_failed_at")
    private Instant feedbackFailedAt;

    @Column(name = "feedback_fail_retriable")
    private Boolean feedbackFailRetriable;

    // 현재 진행 중인 피드백 생성 시도 ID(V30). 발행마다 새로 발급 — FAILED 콜백의 attemptId 가
    // 이 값과 다르면 대체된 이전 시도의 지연 신호로 보고 무시한다(실패 마커 재마킹 방지).
    @Column(name = "feedback_attempt_id", length = 36)
    private String feedbackAttemptId;

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

    // 약점 집중 재도전에서만 채워진다. assignTargetRole 과 같은 이유로 별도 메서드.
    public void assignFocusAreas(String focusAreasJson) {
        this.focusAreas = focusAreasJson;
    }

    // 직무 맞춤 모드의 타깃 회사/JD 부여. create 시그니처를 늘리지 않으려 별도 메서드로 둔다.
    public void assignTargetRole(String companyName, String jobDescription) {
        this.targetCompanyName = companyName;
        this.targetJobDescription = jobDescription;
    }

    // 시간 한도의 기준 시각. 이어하기로 재개했다면 그 자리(sitting)의 시작이 기준이다.
    public Instant durationAnchor() {
        return resumedAt != null ? resumedAt : startedAt;
    }

    public void start() {
        if (status != SessionStatus.READY) {
            throw new IllegalStateException("session is not READY to start (current=" + status + ")");
        }
        this.status = SessionStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    // 중단 → 진행 재개. 조건부 UPDATE(resumeIfInterrupted) 로 DB 는 이미 바뀌었고,
    // 벌크 UPDATE 는 영속성 컨텍스트를 갱신하지 않으므로 인메모리 상태를 맞춘다
    // (start() 뒤에 session.start() 를 부르는 것과 같은 이유). now 는 UPDATE 와 같은 값을 넘긴다.
    public void resume(Instant now) {
        this.status = SessionStatus.IN_PROGRESS;
        this.resumedAt = now;
        this.endedAt = null;
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

    public void beginFeedbackAttempt(String attemptId) {
        this.feedbackAttemptId = attemptId;
    }

    public void markFeedbackFailed(Boolean retriable) {
        this.feedbackFailedAt = Instant.now();
        this.feedbackFailRetriable = retriable;
    }

    public void clearFeedbackFailure() {
        this.feedbackFailedAt = null;
        this.feedbackFailRetriable = null;
    }

    public boolean hasFeedbackFailure() {
        return feedbackFailedAt != null;
    }

    // 메인(일반) 질문만 센다. 꼬리질문은 maxQuestions 한도에 포함하지 않는다
    // (꼬리질문은 maxFollowupsPerQuestion 으로 별도 제한).
    public void incrementQuestionCount() {
        if (totalQuestionCount == null) {
            totalQuestionCount = 0;
        }
        totalQuestionCount++;
    }

    // 던진 메인질문 수가 maxQuestions 에 도달했는지. 꼬리 사이클 종료 후 advanceToNextGeneral 에서 판정.
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

    // 세션은 soft delete 만 지원한다. 하드 DELETE 는 interview_messages 등 자식 FK 에
    // ON DELETE 절이 없어 항상 제약 위반으로 실패한다(모든 세션은 생성 즉시 자기소개
    // 질문이 삽입되므로 자식 없는 세션이 존재하지 않는다).
    public void markDeleted() {
        this.deleted = true;
    }
}
