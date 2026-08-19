package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.BookmarkedQuestionResult;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class QuestionBookmarkServiceTest {

    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @InjectMocks QuestionBookmarkService service;

    @Test
    void setBookmark_marksQuestion() {
        InterviewSession session = sessionFixture(10L);
        InterviewMessage question = questionFixture(session, 100L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(100L)).thenReturn(Optional.of(question));

        assertThat(service.setBookmark(1L, 10L, 100L, true)).isTrue();
        assertThat(question.isBookmarked()).isTrue();
    }

    // 토글이 아니라 명시적 상태를 받으므로 같은 요청이 두 번 와도 결과가 같다.
    @Test
    void setBookmark_isIdempotent() {
        InterviewSession session = sessionFixture(10L);
        InterviewMessage question = questionFixture(session, 100L);
        question.applyBookmark(true);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(100L)).thenReturn(Optional.of(question));

        service.setBookmark(1L, 10L, 100L, true);

        assertThat(question.isBookmarked()).isTrue();
    }

    // 답변을 표시해 봐야 복습할 게 없다.
    @Test
    void setBookmark_rejectsNonQuestionMessage() {
        InterviewSession session = sessionFixture(10L);
        InterviewMessage answer = InterviewMessage.interviewee(session, 2, "제 답변입니다", null, null);
        ReflectionTestUtils.setField(answer, "id", 200L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(200L)).thenReturn(Optional.of(answer));

        assertThatThrownBy(() -> service.setBookmark(1L, 10L, 200L, true))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.MESSAGE_NOT_BOOKMARKABLE));
    }

    @Test
    void setBookmark_rejectsSessionOfAnotherUser() {
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setBookmark(1L, 10L, 100L, true))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.SESSION_NOT_FOUND));

        verify(messageRepository, never()).findById(any());
    }

    // 다른 세션의 메시지 id 를 끼워 넣어도 통과하면 안 된다.
    @Test
    void setBookmark_rejectsMessageOfAnotherSession() {
        InterviewSession session = sessionFixture(10L);
        InterviewMessage foreign = questionFixture(sessionFixture(99L), 100L);

        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(10L, 1L)).thenReturn(Optional.of(session));
        when(messageRepository.findById(100L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.setBookmark(1L, 10L, 100L, true))
            .isInstanceOfSatisfying(DomainException.class, e ->
                assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.MESSAGE_NOT_FOUND));
    }

    // 오답노트는 '질문 + 그때 내 답변 + 복기 재료'가 한 묶음이어야 쓸모가 있다.
    @Test
    void list_joinsAnswerAndCoachingToEachQuestion() {
        InterviewSession session = sessionFixture(10L);
        InterviewMessage question = questionFixture(session, 100L);
        InterviewMessage answer = InterviewMessage.interviewee(session, 2, "제 답변", question, null);
        ReflectionTestUtils.setField(answer, "id", 200L);
        answer.recordCoaching("모범 답안", "리라이트", "한 줄 코칭");

        when(messageRepository.findBookmarkedByOwner(1L)).thenReturn(List.of(question));
        when(messageRepository.findByParentMessage_IdIn(List.of(100L))).thenReturn(List.of(answer));

        List<BookmarkedQuestionResult> results = service.list(1L);

        assertThat(results).hasSize(1);
        BookmarkedQuestionResult r = results.get(0);
        assertThat(r.messageId()).isEqualTo(100L);
        assertThat(r.sessionId()).isEqualTo(10L);
        assertThat(r.question()).isEqualTo("ACID 를 설명해 주세요.");
        assertThat(r.myAnswer()).isEqualTo("제 답변");
        assertThat(r.modelAnswer()).isEqualTo("모범 답안");
        assertThat(r.coachingComment()).isEqualTo("한 줄 코칭");
    }

    // 답변 전에 표시했거나 피드백이 아직 없으면 복기 재료가 비어 있을 뿐, 목록은 나와야 한다.
    @Test
    void list_returnsQuestionEvenWithoutAnswer() {
        InterviewSession session = sessionFixture(10L);
        InterviewMessage question = questionFixture(session, 100L);

        when(messageRepository.findBookmarkedByOwner(1L)).thenReturn(List.of(question));
        when(messageRepository.findByParentMessage_IdIn(List.of(100L))).thenReturn(List.of());

        List<BookmarkedQuestionResult> results = service.list(1L);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).myAnswer()).isNull();
        assertThat(results.get(0).modelAnswer()).isNull();
    }

    @Test
    void list_skipsAnswerLookupWhenNoBookmarks() {
        when(messageRepository.findBookmarkedByOwner(1L)).thenReturn(List.of());

        assertThat(service.list(1L)).isEmpty();
        verify(messageRepository, never()).findByParentMessage_IdIn(any());
    }

    private InterviewMessage questionFixture(InterviewSession session, Long id) {
        InterviewMessage question =
            InterviewMessage.interviewer(session, 1, "ACID 를 설명해 주세요.");
        ReflectionTestUtils.setField(question, "id", id);
        return question;
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(
            user, "백엔드 모의면접", null, SessionMode.TECHNICAL,
            List.of(JobCategory.BACKEND), 5, 30, null, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }
}
