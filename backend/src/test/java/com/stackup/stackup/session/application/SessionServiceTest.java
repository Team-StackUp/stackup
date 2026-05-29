package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionContext;
import com.stackup.stackup.session.domain.SessionContextRepository;
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
            "title", "memo", SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND,
            5, 30, List.of()
        ));

        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.status()).isEqualTo(SessionStatus.READY);
        verify(events).publishEvent(any(SessionCreatedEvent.class));
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
            "t", null, SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND,
            5, 30, List.of(7L, 7L)
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
            "t", null, SessionMode.ONLINE, InterviewType.TECHNICAL, JobCategory.BACKEND,
            5, 30, List.of(8L)
        ))).isInstanceOf(DomainException.class);
    }

    @Test
    void start_transitionsReadyToInProgress() {
        InterviewSession session = sessionFixture(50L);
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));

        SessionResult result = service.start(1L, 50L);

        assertThat(result.status()).isEqualTo(SessionStatus.IN_PROGRESS);
    }

    @Test
    void start_throwsWhenAlreadyInProgress() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.start(1L, 50L))
            .isInstanceOf(DomainException.class);
    }

    @Test
    void end_transitionsInProgressToCompleted() {
        InterviewSession session = sessionFixture(50L);
        session.start();
        when(sessionRepository.findByIdAndUser_IdAndDeletedFalse(50L, 1L))
            .thenReturn(Optional.of(session));

        SessionResult result = service.end(1L, 50L);

        assertThat(result.status()).isEqualTo(SessionStatus.COMPLETED);
    }

    private User userFixture(Long id) {
        User user = User.createGithubUser(123L, "octocat", null, null, "tok");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private InterviewSession sessionFixture(Long id) {
        InterviewSession s = InterviewSession.create(
            userFixture(1L), "t", null, SessionMode.ONLINE,
            InterviewType.TECHNICAL, JobCategory.BACKEND, 5, 30
        );
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private AnalyzedDocument analyzedDocFixture(Long id, AnalysisStatus status) {
        AnalyzedDocument doc = mock(AnalyzedDocument.class);
        org.mockito.Mockito.lenient().when(doc.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(doc.getAnalysisStatus()).thenReturn(status);
        return doc;
    }
}
