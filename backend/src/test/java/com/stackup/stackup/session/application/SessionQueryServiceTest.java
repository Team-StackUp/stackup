package com.stackup.stackup.session.application;

import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.application.exception.SessionNotFoundException;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionQueryServiceTest {

    @Mock InterviewSessionRepository sessionRepo;
    @Mock InterviewMessageRepository messageRepo;
    @InjectMocks SessionQueryService service;

    @Test
    void get_returns_session_when_owned() {
        InterviewSession s = inProgress(99L, 1L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        SessionResult r = service.get(99L, 1L);
        assertThat(r.id()).isEqualTo(99L);
    }

    @Test
    void get_throws_forbidden_when_other_user() {
        InterviewSession s = inProgress(99L, 2L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.get(99L, 1L))
            .isInstanceOf(SessionForbiddenException.class);
    }

    @Test
    void get_throws_not_found_when_session_missing() {
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(99L, 1L))
            .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void list_paginates_sessions_by_user() {
        when(sessionRepo.findByUser_IdAndIsDeletedFalseOrderByCreatedAtDesc(eq(1L), any()))
            .thenReturn(new PageImpl<>(List.of(inProgress(99L, 1L))));
        Page<SessionResult> p = service.list(1L, PageRequest.of(0, 20));
        assertThat(p.getTotalElements()).isEqualTo(1);
    }

    @Test
    void getMessages_returns_sequence_ordered() {
        InterviewSession s = inProgress(99L, 1L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        when(messageRepo.findBySession_IdOrderBySequenceNumberAsc(99L))
            .thenReturn(List.of(interviewer(s, 1, 501L), interviewee(s, 2, 502L)));
        List<MessageResult> ms = service.getMessages(99L, 1L);
        assertThat(ms).hasSize(2);
        assertThat(ms.get(0).sequenceNumber()).isEqualTo(1);
    }

    @Test
    void getMessages_throws_forbidden_when_other_user() {
        InterviewSession s = inProgress(99L, 2L);
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.getMessages(99L, 1L))
            .isInstanceOf(SessionForbiddenException.class);
    }

    @Test
    void getMessages_throws_not_found_when_session_missing() {
        when(sessionRepo.findByIdAndIsDeletedFalse(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getMessages(99L, 1L))
            .isInstanceOf(SessionNotFoundException.class);
    }

    
    private User userStub(Long id) {
        User user = User.createGithubUser(
            123L, "stub-user", "stub@example.com", null, "encrypted-token");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InterviewSession sessionStub(Long sessionId, Long userId, SessionStatus status) {
        User user = userStub(userId);
        InterviewSession s = InterviewSession.create(
            user, "Test Session", null,
            SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND,
            10, 60);
        ReflectionTestUtils.setField(s, "id", sessionId);
        ReflectionTestUtils.setField(s, "status", status);
        return s;
    }

    private InterviewSession inProgress(Long sessionId, Long userId) {
        return sessionStub(sessionId, userId, SessionStatus.IN_PROGRESS);
    }

    private InterviewMessage interviewer(InterviewSession session, int seq, Long id) {
        InterviewMessage m = InterviewMessage.interviewer(session, seq, "면접 질문입니다.", null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }

    private InterviewMessage interviewee(InterviewSession session, int seq, Long id) {
        InterviewMessage m = InterviewMessage.interviewee(session, seq, "답변입니다.", null);
        ReflectionTestUtils.setField(m, "id", id);
        return m;
    }
}
