package com.stackup.stackup.session.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.GenerateFeedbackPayload;
import com.stackup.stackup.session.application.dto.GenerateFeedbackPayload.MessageEvaluation;
import com.stackup.stackup.session.application.dto.GenerateFeedbackPayload.MessageItem;
import com.stackup.stackup.session.application.dto.GenerateFeedbackPayload.VoiceAnalysisSummary;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageVoiceAnalysis;
import com.stackup.stackup.session.domain.MessageVoiceAnalysisRepository;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 세션 COMPLETED commit 후 발화 → generate.feedback 발행 (US-24).
// 멱등: session_feedbacks UNIQUE (session_id). 이미 피드백이 있으면 skip.
@Component
@RequiredArgsConstructor
public class SessionFeedbackRequester {

    private static final Logger log = LoggerFactory.getLogger(SessionFeedbackRequester.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final SessionContextRepository contextRepository;
    private final SessionFeedbackRepository feedbackRepository;
    private final MessageVoiceAnalysisRepository voiceAnalysisRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSessionEnded(SessionEndedEvent event) {
        if (feedbackRepository.existsBySession_Id(event.sessionId())) {
            log.info("generate.feedback skipped — feedback already exists. sessionId={}", event.sessionId());
            return;
        }
        InterviewSession session = sessionRepository.findById(event.sessionId()).orElse(null);
        if (session == null || session.isDeleted()) {
            log.warn("generate.feedback skipped — session missing/deleted. sessionId={}", event.sessionId());
            return;
        }

        List<MessageItem> messages = messageRepository
            .findBySession_IdOrderBySequenceNumberAsc(event.sessionId()).stream()
            .map(this::toItem)
            .toList();
        List<Long> contextDocumentIds = contextRepository.findBySession_Id(event.sessionId()).stream()
            .map(c -> c.getDocument().getId())
            .toList();

        GenerateFeedbackPayload payload = new GenerateFeedbackPayload(
            session.getId(),
            session.getMode().name(),
            session.getJobCategory().name(),
            session.getTotalQuestionCount(),
            event.reason(),
            messages,
            contextDocumentIds,
            summarizeVoiceAnalysis(event.sessionId())
        );

        publisher.publishToAi(
            properties.routingKeys().generateFeedback(),
            payload,
            new MessageContext(event.userId(), session.getId(), null, null)
        );
        log.info("generate.feedback published. sessionId={}, msgCount={}, ctx={}, reason={}",
            session.getId(), messages.size(), contextDocumentIds.size(), event.reason());
    }

    private MessageItem toItem(InterviewMessage m) {
        Long parentId = m.getParentMessage() == null ? null : m.getParentMessage().getId();
        MessageEvaluation evaluation = null;
        boolean hasEval = m.getAnswerSpecificity() != null || m.getAnswerLogic() != null
            || m.getAnswerStructure() != null || m.getAnswerCorrectness() != null;
        if (m.getRole() == com.stackup.stackup.session.domain.MessageRole.INTERVIEWEE && hasEval) {
            evaluation = new MessageEvaluation(
                m.getAnswerSpecificity(),
                m.getAnswerLogic(),
                m.getAnswerStructure(),
                m.getAnswerCorrectness()
            );
        }
        return new MessageItem(
            m.getId(),
            m.getSequenceNumber(),
            m.getRole().name(),
            m.getContent(),
            parentId,
            evaluation
        );
    }

    private VoiceAnalysisSummary summarizeVoiceAnalysis(Long sessionId) {
        List<MessageVoiceAnalysis> analyses = voiceAnalysisRepository.findByMessage_Session_Id(sessionId);
        if (analyses.isEmpty()) {
            return new VoiceAnalysisSummary(0, null, 0.0, Map.of());
        }

        double speakingRateTotal = 0.0;
        int speakingRateCount = 0;
        double silenceTotal = 0.0;
        Map<String, Integer> fillerCounts = new TreeMap<>();

        for (MessageVoiceAnalysis analysis : analyses) {
            if (analysis.getSpeakingRateWpm() != null) {
                speakingRateTotal += analysis.getSpeakingRateWpm();
                speakingRateCount++;
            }
            if (analysis.getSilenceDurationSec() != null) {
                silenceTotal += analysis.getSilenceDurationSec();
            }
            mergeFillerCounts(fillerCounts, analysis.getFillerWordCounts());
        }

        Double averageSpeakingRate = speakingRateCount == 0 ? null : speakingRateTotal / speakingRateCount;
        return new VoiceAnalysisSummary(analyses.size(), averageSpeakingRate, silenceTotal, fillerCounts);
    }

    private void mergeFillerCounts(Map<String, Integer> totals, String fillerWordCountsJson) {
        if (fillerWordCountsJson == null || fillerWordCountsJson.isBlank()) {
            return;
        }
        try {
            JsonNode root = JSON.readTree(fillerWordCountsJson);
            if (!root.isObject()) {
                return;
            }
            root.fields().forEachRemaining(entry -> {
                String word = entry.getKey();
                JsonNode value = entry.getValue();
                if (word == null || word.isBlank() || !value.canConvertToInt()) {
                    return;
                }
                int count = value.asInt();
                if (count <= 0) {
                    return;
                }
                totals.merge(word, count, Integer::sum);
            });
        } catch (Exception e) {
            log.warn("filler_word_counts json parse failed. raw={}", fillerWordCountsJson, e);
        }
    }
}
