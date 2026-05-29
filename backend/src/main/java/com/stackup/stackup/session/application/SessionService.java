package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionContext;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final InterviewSessionRepository sessionRepo;
    private final SessionContextRepository contextRepo;
    private final InterviewMessageRepository messageRepo;
    private final AnalyzedDocumentRepository documentRepo;
    private final UserRepository userRepo;
    private final ProcessedMessageRepository processedRepo;
    private final SseEventPublisher sse;
    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional
    public SessionResult create(SessionCreateCommand cmd) {
        User user = userRepo.findById(cmd.userId())
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + cmd.userId()));

        List<AnalyzedDocument> docs = (cmd.contextDocumentIds() == null || cmd.contextDocumentIds().isEmpty())
                ? List.of()
                : documentRepo.findAllById(cmd.contextDocumentIds());

        for (AnalyzedDocument doc : docs) {
            Long ownerId = doc.getResume() != null
                    ? doc.getResume().getUser().getId()
                    : doc.getRepository().getUser().getId();
            if (!ownerId.equals(user.getId())) {
                throw new SessionForbiddenException(doc.getId());
            }
        }

        InterviewSession session = sessionRepo.save(InterviewSession.create(
                user, cmd.title(), cmd.memo(),
                cmd.mode(), cmd.interviewType(), cmd.jobCategory(),
                cmd.maxQuestions(), cmd.maxDurationMinutes()
        ));

        if (!docs.isEmpty()) {
            List<SessionContext> contexts = docs.stream()
                    .map(d -> SessionContext.of(session, d))
                    .toList();
            contextRepo.saveAll(contexts);
        }

        events.publishEvent(new SessionCreatedEvent(
                session.getId(),
                user.getId(),
                new GenerateQuestionsPayload(
                        session.getId(),
                        cmd.interviewType().name(),
                        cmd.jobCategory().name(),
                        cmd.contextDocumentIds() == null ? List.of() : cmd.contextDocumentIds(),
                        session.getMaxQuestions()
                )
        ));

        return SessionResult.from(session);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionCreated(SessionCreatedEvent event) {
        publisher.publishToAi(
                properties.routingKeys().generateQuestions(),
                event.payload(),
                new MessageContext(event.userId(), event.sessionId(), null, null)
        );
    }
}
