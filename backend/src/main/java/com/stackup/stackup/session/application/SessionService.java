package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.sse.SseEventPublisher;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.GenerateFollowupPayload;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.MessageSubmitCommand;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.application.exception.SessionInvalidStateException;
import com.stackup.stackup.session.application.exception.SessionNotFoundException;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionContext;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final String ANSWER_IDEMPOTENCY_CONSUMER = "session.answer";

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

    @Transactional
    public MessageResult submitAnswer(MessageSubmitCommand cmd) {
        InterviewSession session = sessionRepo.findByIdAndIsDeletedFalse(cmd.sessionId())
                .orElseThrow(() -> new SessionNotFoundException(cmd.sessionId()));

        if (!session.getUser().getId().equals(cmd.userId())) {
            throw new SessionForbiddenException(cmd.sessionId());
        }

        String idemKey = idempotencyKey(cmd.sessionId(), cmd.idempotencyKey());
        if (idemKey != null && processedRepo.existsById(idemKey)) {
            InterviewMessage last = messageRepo
                    .findFirstBySession_IdOrderBySequenceNumberDesc(cmd.sessionId())
                    .orElseThrow(() -> new IllegalStateException("멱등 캐시는 있는데 메시지가 없습니다."));
            return MessageResult.from(last);
        }

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new SessionInvalidStateException(cmd.sessionId(),
                    "IN_PROGRESS 가 아닙니다. 현재=" + session.getStatus());
        }

        InterviewMessage parentQuestion = messageRepo
                .findFirstBySession_IdOrderBySequenceNumberDesc(cmd.sessionId())
                .orElseThrow(() -> new SessionInvalidStateException(cmd.sessionId(), "직전 질문이 없습니다."));

        if (parentQuestion.getRole() != MessageRole.INTERVIEWER) {
            throw new SessionInvalidStateException(cmd.sessionId(),
                    "직전 메시지가 질문이 아닙니다. 꼬리질문을 기다리세요. role=" + parentQuestion.getRole());
        }

        int nextSeq = messageRepo.findMaxSequenceBySessionId(cmd.sessionId()) + 1;
        InterviewMessage answer = messageRepo.save(
                InterviewMessage.interviewee(session, nextSeq, cmd.content(), parentQuestion));

        if (idemKey != null) {
            try {
                processedRepo.save(ProcessedMessage.of(idemKey, ANSWER_IDEMPOTENCY_CONSUMER));
            } catch (DataIntegrityViolationException ignored) {
                // race: 다른 worker 가 먼저 캐싱했으므로 그대로 진행
            }
        }

        events.publishEvent(new AnswerSubmittedEvent(
                session.getId(), session.getUser().getId(),
                parentQuestion.getId(), answer.getId(), cmd.content()));

        return MessageResult.from(answer);
    }

    @Transactional
    public SessionResult end(Long sessionId, Long userId, boolean cancel) {
        InterviewSession session = sessionRepo.findByIdAndIsDeletedFalse(sessionId)
                .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!session.getUser().getId().equals(userId)) {
            throw new SessionForbiddenException(sessionId);
        }
        if (session.getStatus() == SessionStatus.COMPLETED || session.getStatus() == SessionStatus.CANCELLED) {
            throw new SessionInvalidStateException(sessionId, "이미 종료된 세션입니다. 현재=" + session.getStatus());
        }
        if (cancel) {
            session.cancel();
        } else {
            session.end();
        }
        sse.publishToSession(sessionId, SseEventType.SESSION_STATE,
                Map.of("sessionId", sessionId, "state", session.getStatus().name(),
                        "totalQuestionCount", session.getTotalQuestionCount(),
                        "endedAt", session.getEndedAt()));
        return SessionResult.from(session);
    }

    private String idempotencyKey(Long sessionId, String raw) {
        if (raw == null || raw.isBlank()) return null;
        return ANSWER_IDEMPOTENCY_CONSUMER + ":" + sessionId + ":" + raw;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswerSubmitted(AnswerSubmittedEvent event) {
        publisher.publishToAi(
                properties.routingKeys().generateFollowup(),
                new GenerateFollowupPayload(
                        event.sessionId(), event.parentQuestionMessageId(),
                        event.answerMessageId(), event.answerText(), null),
                new MessageContext(event.userId(), event.sessionId(), null, null)
        );
    }
}
