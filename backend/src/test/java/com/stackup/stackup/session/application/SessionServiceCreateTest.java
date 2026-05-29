package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceCreateTest {

    @Mock InterviewSessionRepository sessionRepo;
    @Mock SessionContextRepository contextRepo;
    @Mock InterviewMessageRepository messageRepo;
    @Mock AnalyzedDocumentRepository documentRepo;
    @Mock UserRepository userRepo;
    @Mock ProcessedMessageRepository processedRepo;
    @Mock SseEventPublisher sse;
    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock ApplicationEventPublisher events;

    @InjectMocks SessionService service;

    @Test
    void create_inserts_session_contexts_and_publishes_event() {
        User user = userStub(1L);
        AnalyzedDocument doc1 = docStub(11L, user);
        AnalyzedDocument doc2 = docStub(12L, user);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(documentRepo.findAllById(List.of(11L, 12L))).thenReturn(List.of(doc1, doc2));
        when(sessionRepo.save(any())).thenAnswer(inv -> {
            InterviewSession s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 99L);
            return s;
        });

        SessionResult r = service.create(new SessionCreateCommand(
                1L, "Title", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
                JobCategory.BACKEND, 10, 60, List.of(11L, 12L)));

        assertThat(r.id()).isEqualTo(99L);
        assertThat(r.status()).isEqualTo(SessionStatus.READY);
        verify(sessionRepo).save(any(InterviewSession.class));
        verify(contextRepo).saveAll(argThat((List<SessionContext> l) -> l.size() == 2));
        verify(events).publishEvent(any(SessionCreatedEvent.class));
    }

    @Test
    void create_throws_when_user_missing() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(stubCmd(1L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_throws_when_document_not_owned_by_user() {
        User user = userStub(1L);
        AnalyzedDocument doc = docStub(11L, userStub(2L)); // 다른 user
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(documentRepo.findAllById(List.of(11L))).thenReturn(List.of(doc));
        assertThatThrownBy(() -> service.create(stubCmd(1L, 11L)))
                .isInstanceOf(SessionForbiddenException.class);
    }

    private User userStub(Long id) {
        User user = User.createGithubUser(
                123L, "stub-user", "stub@example.com", null, "encrypted-token"
        );
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private AnalyzedDocument docStub(Long docId, User owner) {
        Resume resume = Resume.create(owner, "resume.pdf", "/path/to/resume.pdf", ResumeFileType.PDF, 1024L);
        ReflectionTestUtils.setField(resume, "id", docId * 100); // arbitrary resume id
        AnalyzedDocument doc = AnalyzedDocument.forResume(resume);
        ReflectionTestUtils.setField(doc, "id", docId);
        return doc;
    }

    private SessionCreateCommand stubCmd(Long userId, Long... docIds) {
        List<Long> ids = docIds.length == 0 ? List.of() : List.of(docIds);
        return new SessionCreateCommand(
                userId, "Title", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
                JobCategory.BACKEND, 10, 60, ids
        );
    }
}
