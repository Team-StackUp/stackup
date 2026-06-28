package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.GenerateFollowupPayload;
import com.stackup.stackup.session.application.dto.GenerateFollowupPayload.HistoryItem;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 답변 commit 후 발화 → generate.followup envelope 발행 (US-19).
@Component
@RequiredArgsConstructor
public class SessionFollowupRequester {

    private static final Logger log = LoggerFactory.getLogger(SessionFollowupRequester.class);

    private static final int HISTORY_MAX = 6;

    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final InterviewMessageRepository messageRepository;
    private final SessionContextRepository contextRepository;
    private final QuestionsCallbackService questionsCallbackService;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnswerSubmitted(AnswerSubmittedEvent event) {
        InterviewMessage parent = messageRepository.findById(event.parentQuestionMessageId()).orElse(null);
        InterviewMessage answer = messageRepository.findById(event.answerMessageId()).orElse(null);
        if (parent == null || answer == null) {
            log.warn("generate.followup skipped — message not found. parent={}, answer={}",
                event.parentQuestionMessageId(), event.answerMessageId());
            return;
        }
        InterviewSession session = parent.getSession();

        // 종료된 세션이면 꼬리질문/풀 생성을 요청하지 않는다. maxDuration 직전 제출한 답변의
        // AFTER_COMMIT 발화가 스위퍼 자동종료와 겹칠 때, 종료 세션에 placeholder·질문이
        // 추가되거나 불필요한 AI 호출이 발생하는 것을 방지(콜백단 terminal 가드와 같은 선상).
        if (session.getStatus().isTerminal()) {
            log.info("generate.followup skipped — session already terminal. sessionId={}, status={}",
                session.getId(), session.getStatus());
            return;
        }

        // RAG(자료 근거/correctness) 용 세션 컨텍스트 문서 — 피드백 발행부와 동일 패턴.
        List<Long> contextDocumentIds = contextRepository.findBySession_Id(session.getId()).stream()
            .map(c -> c.getDocument().getId())
            .toList();

        // 자기소개(첫 질문) 답변이면 꼬리질문을 만들지 않고, 이 답변을 씨앗으로 질문 풀 생성을 트리거한다.
        if (parent.isSelfIntroduction()) {
            events.publishEvent(new SelfIntroAnsweredEvent(
                event.userId(),
                session.getId(),
                session.getMode(),
                new ArrayList<>(session.getJobCategories()),
                session.getMaxQuestions(),
                session.getGeneralQuestionCount(),
                contextDocumentIds,
                answer.getContent(),
                session.getTargetCompanyName(),
                session.getTargetJobDescription()
            ));
            log.info("self-intro answered — requesting question pool. sessionId={}", session.getId());
            return;
        }

        // 최근 대화 히스토리 (중복 질문 회피). 시퀀스 순 → 마지막 N개.
        List<InterviewMessage> ordered =
            messageRepository.findBySession_IdOrderBySequenceNumberAsc(session.getId());

        // 깊이 제어: 현재 일반질문 아래 꼬리질문 수(depth) 가 m 이상이면
        // 꼬리질문 대신 풀의 다음 일반질문으로 넘어간다(없으면 종료). AI 호출 불필요.
        int depth = currentFollowupDepth(ordered);
        int maxFollowups = session.getMaxFollowupsPerQuestion() != null
            ? session.getMaxFollowupsPerQuestion() : 2;
        if (depth >= maxFollowups) {
            log.info("followup depth reached (m={}) — advancing to next general. sessionId={}",
                maxFollowups, session.getId());
            questionsCallbackService.advanceToNextGeneral(session.getId());
            return;
        }

        List<HistoryItem> history = ordered.stream()
            .skip(Math.max(0, ordered.size() - HISTORY_MAX))
            .map(m -> new HistoryItem(m.getRole().name(), m.getContent()))
            .toList();

        // 스트리밍 표시용 placeholder 선INSERT (seq 예약). 콜백에서 content 채움.
        int placeholderSeq = (int) messageRepository.countBySession_Id(session.getId()) + 1;
        InterviewMessage placeholder = messageRepository.save(
            InterviewMessage.followupPlaceholder(session, placeholderSeq, parent));
        // placeholder 를 프론트 메시지 목록에 즉시 노출 → 토큰 델타가 채울 버블 확보.
        // REQUIRES_NEW 트랜잭션 commit 후 RealtimeNotifyEventListener 가 발행하므로 조회 보장.
        events.publishEvent(RealtimeNotifyEvent.session(
            session.getId(), SseEventType.SESSION_MESSAGE, placeholder.getId()));

        GenerateFollowupPayload payload = new GenerateFollowupPayload(
            session.getId(),
            parent.getId(),
            answer.getId(),
            parent.getContent(),
            answer.getContent(),
            session.getMode(),
            session.getJobCategory(),
            contextDocumentIds,
            parent.getCategory(),
            parent.getExpectedSignal(),
            placeholder.getId(),
            history
        );
        publisher.publishToAi(
            properties.routingKeys().generateFollowup(),
            payload,
            new MessageContext(event.userId(), session.getId(), null, null)
        );
        log.info("generate.followup published. sessionId={}, parent={}, answer={}",
            session.getId(), parent.getId(), answer.getId());
    }

    // 현재 일반질문(INTERVIEWER + parent null) 이후 달린 꼬리질문(INTERVIEWER + parent 있음) 수.
    // 새 일반질문이 나오면 0 으로 리셋된다.
    private int currentFollowupDepth(List<InterviewMessage> ordered) {
        int depth = 0;
        for (InterviewMessage m : ordered) {
            if (m.getRole() != MessageRole.INTERVIEWER || m.isClarification()) {
                continue;             // 부연 메시지는 깊이에 미반영
            }
            if (m.getParentMessage() == null) {
                depth = 0;            // 일반질문 → 리셋
            } else {
                depth++;              // 꼬리질문 → 증가
            }
        }
        return depth;
    }
}
