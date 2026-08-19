package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionContext;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock SessionContextRepository contextRepository;
    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock UserRepository userRepository;
    @Mock SessionFeedbackRepository feedbackRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks SessionService service;

    @Test
    void create_savesSessionAndPublishesEvent() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });

        SessionResult result = service.create(1L, new SessionCreateCommand(
            "title", "memo", SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            5, 30, null, null, List.of(), null, null
        ));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(SessionStatus.READY);
        verify(events).publishEvent(any(SessionCreatedEvent.class));
    }

    @Test
    void create_generatesTitleFromModeAndJobWhenBlank() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResult result = service.create(1L, new SessionCreateCommand(
            "  ", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            5, 30, null, null, List.of(), null, null
        ));

        assertThat(result.title()).isEqualTo("백엔드 기술 면접");
    }

    @Test
    void create_keepsProvidedTitle() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));

        SessionResult result = service.create(1L, new SessionCreateCommand(
            "내가 정한 제목", null, SessionMode.INTEGRATED, List.of(JobCategory.FRONTEND),
            5, 30, null, null, List.of(), null, null
        ));

        assertThat(result.title()).isEqualTo("내가 정한 제목");
    }

    @Test
    void create_linksAnalyzedContextDocuments() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });
        AnalyzedDocument doc = analyzedDocFixture(7L, AnalysisStatus.ANALYZED);
        when(documentRepository.findActiveByIdAndOwner(7L, 1L)).thenReturn(Optional.of(doc));

        SessionResult result = service.create(1L, new SessionCreateCommand(
            "t", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            5, 30, null, null, List.of(7L, 7L), null, null
        ));

        assertThat(result.contextDocumentIds()).containsExactly(7L);
        verify(contextRepository).save(any(SessionContext.class));
    }

    @Test
    void create_rejectsNonAnalyzedDocument() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> inv.getArgument(0));
        AnalyzedDocument pending = analyzedDocFixture(8L, AnalysisStatus.PROCESSING);
        when(documentRepository.findActiveByIdAndOwner(8L, 1L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.create(1L, new SessionCreateCommand(
            "t", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            5, 30, null, null, List.of(8L), null, null
        ))).isInstanceOf(DomainException.class);
    }

    @Test
    void create_rejectsMaxQuestionsBelowGeneralCount() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

        // 상한(2) < 일반질문(5) — 통과시키면 AI 가 만든 풀 대부분이 버려진다.
        // 프론트가 막고 있지만 API 직접 호출은 막을 게 없어 서버에서도 검증한다.
        assertThatThrownBy(() -> service.create(1L, new SessionCreateCommand(
            "t", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            2, 30, 5, 2, List.of(), null, null
        ))).isInstanceOf(DomainException.class);

        verify(sessionRepository, never()).save(any(InterviewSession.class));
    }

    @Test
    void create_jobTailoredRequiresJobDescription() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));

        // JD 미입력 → SESSION_JD_REQUIRED. save 까지 가지 않고 검증에서 막힌다.
        assertThatThrownBy(() -> service.create(1L, new SessionCreateCommand(
            "t", null, SessionMode.JOB_TAILORED, List.of(JobCategory.BACKEND),
            5, 30, null, null, List.of(), "토스", "  "
        ))).isInstanceOf(DomainException.class);
    }

    @Test
    void create_jobTailoredStoresCompanyAndJd() {
        User user = userFixture(1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 100L);
            return s;
        });

        SessionResult result = service.create(1L, new SessionCreateCommand(
            "  ", null, SessionMode.JOB_TAILORED, List.of(JobCategory.BACKEND),
            5, 30, null, null, List.of(), "토스", "백엔드 엔지니어. Kotlin/Spring, 대용량 결제."
        ));

        assertThat(result.targetCompanyName()).isEqualTo("토스");
        assertThat(result.targetJobDescription()).contains("결제");
        // 제목 미입력 시 회사명이 앞에 붙는다.
        assertThat(result.title()).isEqualTo("토스 백엔드 직무 맞춤 면접");
    }

    // ── retry (같은 설정으로 다시) ─────────────────────────────────────────────

    @Test
    void retry_copiesSettingsIntoNewSession() {
        User user = userFixture(1L);
        InterviewSession source = InterviewSession.create(
            user, "백엔드 모의면접", "메모", SessionMode.JOB_TAILORED,
            List.of(JobCategory.BACKEND, JobCategory.INFRA), 8, 45, 4, 3
        );
        ReflectionTestUtils.setField(source, "id", 50L);
        source.assignTargetRole("스택업", "JD 본문");

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L)).thenReturn(Optional.of(source));
        when(contextRepository.findBySession_Id(50L)).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 101L);
            return s;
        });

        SessionResult result = service.retry(1L, 50L, false);

        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("백엔드 모의면접");
        assertThat(result.memo()).isEqualTo("메모");
        assertThat(result.mode()).isEqualTo(SessionMode.JOB_TAILORED);
        assertThat(result.jobCategories())
            .containsExactly(JobCategory.BACKEND, JobCategory.INFRA);
        assertThat(result.maxQuestions()).isEqualTo(8);
        assertThat(result.maxDurationMinutes()).isEqualTo(45);
        assertThat(result.generalQuestionCount()).isEqualTo(4);
        assertThat(result.maxFollowupsPerQuestion()).isEqualTo(3);
        // JOB_TAILORED 의 JD 가 빠지면 새 세션이 SESSION_JD_REQUIRED 로 막힌다.
        assertThat(result.targetCompanyName()).isEqualTo("스택업");
        assertThat(result.targetJobDescription()).isEqualTo("JD 본문");
        assertThat(result.status()).isEqualTo(SessionStatus.READY);
        verify(events).publishEvent(any(SessionCreatedEvent.class));
    }

    // 원본 설정을 그대로 재전송하면 그 사이 삭제된 자료 하나 때문에 404 로 전체가 막힌다.
    @Test
    void retry_skipsDeletedOrUnanalyzedContextDocuments() {
        User user = userFixture(1L);
        InterviewSession source = sessionFixture(50L);
        // mock 생성·스터빙은 when(...) 바깥에서 먼저 끝낸다(중첩 스터빙 금지).
        AnalyzedDocument alive = analyzedDocFixture(7L, AnalysisStatus.ANALYZED);
        AnalyzedDocument deleted = analyzedDocFixture(8L, AnalysisStatus.ANALYZED);
        AnalyzedDocument reanalyzing = analyzedDocFixture(9L, AnalysisStatus.PROCESSING);
        List<SessionContext> contexts = List.of(
            contextFixture(source, alive),
            contextFixture(source, deleted),      // 그 사이 삭제됨
            contextFixture(source, reanalyzing)   // 재분석 중
        );

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L)).thenReturn(Optional.of(source));
        when(contextRepository.findBySession_Id(50L)).thenReturn(contexts);
        when(documentRepository.findActiveByIdAndOwner(7L, 1L)).thenReturn(Optional.of(alive));
        when(documentRepository.findActiveByIdAndOwner(8L, 1L)).thenReturn(Optional.empty());
        when(documentRepository.findActiveByIdAndOwner(9L, 1L)).thenReturn(Optional.of(reanalyzing));
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 101L);
            return s;
        });

        SessionResult result = service.retry(1L, 50L, false);

        // 살아있는 7L 만 다시 연결 — 나머지는 조용히 제외한다(호출자가 원본과 비교해 안내).
        assertThat(result.contextDocumentIds()).containsExactly(7L);
    }

    // 약점 집중: 기준(70) 미만인 축만, 낮은 순으로 최대 2개.
    @Test
    void retry_marksWeakestAxesAsFocusAreas() {
        User user = userFixture(1L);
        InterviewSession source = sessionFixture(50L);
        SessionFeedback feedback = feedbackFixture(source, 82.0, 55.0, 61.0);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L)).thenReturn(Optional.of(source));
        when(contextRepository.findBySession_Id(50L)).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(feedbackRepository.findBySession_Id(50L)).thenReturn(Optional.of(feedback));
        captureCreatedSession(101L);

        SessionResult result = service.retry(1L, 50L, true);

        // 55(논리) < 61(전달력) < 70 → 둘 다. 82(기술)는 기준 이상이라 제외.
        assertThat(result.focusAreas()).containsExactly("LOGIC", "COMMUNICATION");
    }

    // 전부 기준 이상이어도 가장 낮은 하나는 고른다 — 눌렀는데 아무것도 안 바뀌면 고장으로 보인다.
    @Test
    void retry_picksLowestAxisEvenWhenAllScoresAreStrong() {
        User user = userFixture(1L);
        InterviewSession source = sessionFixture(50L);
        SessionFeedback feedback = feedbackFixture(source, 91.0, 88.0, 95.0);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L)).thenReturn(Optional.of(source));
        when(contextRepository.findBySession_Id(50L)).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(feedbackRepository.findBySession_Id(50L)).thenReturn(Optional.of(feedback));
        captureCreatedSession(101L);

        SessionResult result = service.retry(1L, 50L, true);

        assertThat(result.focusAreas()).containsExactly("LOGIC");
    }

    // 중단 세션은 피드백이 없다 — 집중 영역 없이 일반 재도전과 같아진다.
    @Test
    void retry_withoutFeedbackFallsBackToPlainRetry() {
        User user = userFixture(1L);
        InterviewSession source = sessionFixture(50L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L)).thenReturn(Optional.of(source));
        when(contextRepository.findBySession_Id(50L)).thenReturn(List.of());
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(feedbackRepository.findBySession_Id(50L)).thenReturn(Optional.empty());
        captureCreatedSession(101L);

        SessionResult result = service.retry(1L, 50L, true);

        assertThat(result.focusAreas()).isEmpty();
    }

    @Test
    void retry_rejectsSessionOfAnotherUser() {
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retry(1L, 50L, false))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void start_transitionsReadyToInProgress() {
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.startIfReady(any(), any())).thenReturn(1);

        SessionResult result = service.start(1L, 50L);

        assertThat(result.status()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void start_throwsWhenTransitionNotClaimed() {
        // 조건부 UPDATE 가 0행 — 이미 시작됐거나(동시 start) 종료된 세션.
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.startIfReady(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.start(1L, 50L))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void interrupt_claimsAtomicTransition() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.finishIfInProgress(any(), any(), any())).thenReturn(1);

        SessionResult result = service.interrupt(1L, 50L);

        assertThat(result.status()).isEqualTo(SessionStatus.INTERRUPTED);
        // 중단은 피드백 대상이 아니다 — 종료 이벤트를 발행하면 안 된다.
        verify(events, org.mockito.Mockito.never()).publishEvent(any(SessionEndedEvent.class));
    }

    @Test
    void interrupt_throwsWhenAlreadyFinished() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.finishIfInProgress(any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.interrupt(1L, 50L))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_claimsAtomicTransition() {
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.cancelIfReady(50L)).thenReturn(1);

        SessionResult result = service.cancel(1L, 50L);

        assertThat(result.status()).isEqualTo(SessionStatus.CANCELLED);
    }

    @Test
    void cancel_throwsWhenNotReady() {
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.cancelIfReady(50L)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(1L, 50L))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void end_transitionsInProgressToCompleted() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));
        when(sessionRepository.finishIfInProgress(any(), any(), any())).thenReturn(1);

        SessionResult result = service.end(1L, 50L);

        assertThat(result.status()).isEqualTo(SessionStatus.COMPLETED);
    }

    @Test
    void delete_softDeletesInsteadOfHardDelete() {
        // 하드 DELETE 는 자식 FK(ON DELETE 미지정) 위반으로 항상 실패했다 — soft delete 회귀 방지.
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));

        service.delete(1L, 50L);

        assertThat(session.isDeleted()).isTrue();
        verify(sessionRepository, org.mockito.Mockito.never()).delete(any(InterviewSession.class));
    }

    // create() 가 save 하는 세션에 id 를 매기고, retry 가 다시 찾을 수 있게 findById 도 이어준다.
    private void captureCreatedSession(Long id) {
        InterviewSession[] holder = new InterviewSession[1];
        when(sessionRepository.save(any(InterviewSession.class))).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", id);
            holder[0] = s;
            return s;
        });
        org.mockito.Mockito.lenient().when(sessionRepository.findById(id))
            .thenAnswer(inv -> Optional.ofNullable(holder[0]));
    }

    private SessionFeedback feedbackFixture(
        InterviewSession session, Double technical, Double logic, Double communication) {
        SessionFeedback feedback = mock(SessionFeedback.class);
        org.mockito.Mockito.lenient().when(feedback.getTechnicalAccuracy()).thenReturn(technical);
        org.mockito.Mockito.lenient().when(feedback.getLogicScore()).thenReturn(logic);
        org.mockito.Mockito.lenient().when(feedback.getCommunicationScore()).thenReturn(communication);
        return feedback;
    }

    private User userFixture(Long id) {
        User user = User.createGithubUser(123L, "octocat", null, null, "tok");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InterviewSession sessionFixture(Long id) {
        InterviewSession s = InterviewSession.create(
            userFixture(1L), "t", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private SessionContext contextFixture(InterviewSession session, AnalyzedDocument doc) {
        return SessionContext.link(session, doc);
    }

    private AnalyzedDocument analyzedDocFixture(Long id, AnalysisStatus status) {
        AnalyzedDocument doc = mock(AnalyzedDocument.class);
        org.mockito.Mockito.lenient().when(doc.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(doc.getAnalysisStatus()).thenReturn(status);
        return doc;
    }
}
