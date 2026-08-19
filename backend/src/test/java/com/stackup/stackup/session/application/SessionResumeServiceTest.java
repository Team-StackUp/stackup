package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 이어하기의 본질은 상태 전이가 아니라 **끊긴 턴 복구**다.
 * 중단은 턴 한가운데서 일어나고, 그동안 도착한 콜백은 terminal 가드가 전부 드롭했다.
 */
@ExtendWith(MockitoExtension.class)
class SessionResumeServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock SessionContextRepository contextRepository;
    @Mock SessionQuestionPoolRepository poolRepository;
    @Mock QuestionsCallbackService questionsCallbackService;
    @Mock ApplicationEventPublisher events;
    @InjectMocks SessionResumeService service;

    // ── 상태 전이 ─────────────────────────────────────────────────────────────

    @Test
    void resume_rejectsSessionThatIsNotInterrupted() {
        InterviewSession completed = sessionFixture(10L, SessionStatus.COMPLETED);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> service.resume(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));

        verify(sessionRepository, never()).resumeIfInterrupted(anyLong(), any());
    }

    @Test
    void resume_rejectsSessionOfAnotherUser() {
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resume(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_NOT_FOUND));
    }

    // 중복 요청(더블클릭·재전송) 중 하나만 전이를 차지한다.
    @Test
    void resume_failsWhenTransitionIsClaimedByAnotherRequest() {
        InterviewSession interrupted = sessionFixture(10L, SessionStatus.INTERRUPTED);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L))
            .thenReturn(Optional.of(interrupted));
        when(sessionRepository.resumeIfInterrupted(eq(10L), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.resume(1L, 10L))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_INVALID_STATE));
    }

    // ── 턴 복구 ───────────────────────────────────────────────────────────────

    // 답할 질문이 그대로 남아 있으면 건드릴 게 없다.
    @Test
    void resume_leavesAnsweredQuestionAlone() {
        InterviewSession session = resumable(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 3, "ACID 를 설명해 주세요.");
        ReflectionTestUtils.setField(question, "id", 100L);
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(question));

        SessionResult result = service.resume(1L, 10L);

        assertThat(result.status()).isEqualTo(SessionStatus.IN_PROGRESS);
        verify(questionsCallbackService, never()).advanceToNextGeneral(anyLong());
        verify(events, never()).publishEvent(any(SelfIntroAnsweredEvent.class));
    }

    // "(생성 중)" 에서 끊겼다면 그 꼬리질문은 영영 오지 않는다(콜백이 드롭됐다).
    // 그대로 두면 재개해도 화면이 생성 중에 멈춘다.
    @Test
    void resume_failsDanglingPlaceholderAndAdvances() {
        InterviewSession session = resumable(10L);
        InterviewMessage parent = InterviewMessage.interviewer(session, 3, "부모 질문");
        InterviewMessage placeholder = InterviewMessage.followupPlaceholder(session, 4, parent);
        ReflectionTestUtils.setField(placeholder, "id", 101L);
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(placeholder));

        service.resume(1L, 10L);

        assertThat(placeholder.getStatus()).isEqualTo(MessageStatus.FAILED);
        assertThat(placeholder.getContent())
            .isEqualTo(InterviewMessage.FOLLOWUP_GENERATION_FAILED_TEXT);
        verify(questionsCallbackService).advanceToNextGeneral(10L);
    }

    // 답변까지 하고 다음 질문을 못 받은 채 끊긴 경우 — 그냥 재개하면 답할 게 없다.
    @Test
    void resume_advancesWhenAnswerHasNoNextQuestion() {
        InterviewSession session = resumable(10L);
        InterviewMessage question = InterviewMessage.interviewer(session, 3, "일반 질문");
        InterviewMessage answer = InterviewMessage.interviewee(session, 4, "제 답변", question, null);
        ReflectionTestUtils.setField(answer, "id", 102L);
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(answer));

        service.resume(1L, 10L);

        verify(questionsCallbackService).advanceToNextGeneral(10L);
        verify(events, never()).publishEvent(any(SelfIntroAnsweredEvent.class));
    }

    // 자기소개만 답하고 끊겼는데 풀이 아예 없으면, 다음 질문으로 넘길 게 아니라
    // 질문 풀 생성을 다시 요청해야 한다(넘기면 POOL_EXHAUSTED 로 세션이 끝나버린다).
    @Test
    void resume_reRequestsQuestionPoolWhenSelfIntroAnsweredButPoolMissing() {
        InterviewSession session = resumable(10L);
        InterviewMessage selfIntro = InterviewMessage.selfIntroduction(session, 1);
        InterviewMessage answer =
            InterviewMessage.interviewee(session, 2, "안녕하세요, 백엔드 3년차입니다", selfIntro, null);
        ReflectionTestUtils.setField(answer, "id", 103L);
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(answer));
        when(poolRepository.countBySessionId(10L)).thenReturn(0L);

        service.resume(1L, 10L);

        ArgumentCaptor<SelfIntroAnsweredEvent> captor =
            ArgumentCaptor.forClass(SelfIntroAnsweredEvent.class);
        verify(events).publishEvent(captor.capture());
        assertThat(captor.getValue().sessionId()).isEqualTo(10L);
        assertThat(captor.getValue().selfIntroAnswer()).isEqualTo("안녕하세요, 백엔드 3년차입니다");
        verify(questionsCallbackService, never()).advanceToNextGeneral(anyLong());
    }

    // 풀이 이미 있으면(생성은 됐고 다음 질문만 못 받은 것) 다음 질문으로 넘긴다.
    @Test
    void resume_advancesWhenSelfIntroAnsweredAndPoolExists() {
        InterviewSession session = resumable(10L);
        InterviewMessage selfIntro = InterviewMessage.selfIntroduction(session, 1);
        InterviewMessage answer = InterviewMessage.interviewee(session, 2, "자기소개", selfIntro, null);
        ReflectionTestUtils.setField(answer, "id", 104L);
        when(messageRepository.findFirstBySession_IdOrderBySequenceNumberDesc(10L))
            .thenReturn(Optional.of(answer));
        when(poolRepository.countBySessionId(10L)).thenReturn(4L);

        service.resume(1L, 10L);

        verify(questionsCallbackService).advanceToNextGeneral(10L);
        verify(events, never()).publishEvent(any(SelfIntroAnsweredEvent.class));
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    /** 소유권·전이·재조회까지 통과해 복구 단계로 들어가는 세션. */
    private InterviewSession resumable(Long id) {
        InterviewSession interrupted = sessionFixture(id, SessionStatus.INTERRUPTED);
        InterviewSession afterResume = sessionFixture(id, SessionStatus.IN_PROGRESS);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(id, 1L))
            .thenReturn(Optional.of(interrupted));
        when(sessionRepository.resumeIfInterrupted(eq(id), any(Instant.class))).thenReturn(1);
        when(sessionRepository.findById(id)).thenReturn(Optional.of(afterResume));
        lenient().when(contextRepository.findBySession_Id(id)).thenReturn(List.of());
        return afterResume;
    }

    private InterviewSession sessionFixture(Long id, SessionStatus status) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "면접", null, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND), 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }
}
