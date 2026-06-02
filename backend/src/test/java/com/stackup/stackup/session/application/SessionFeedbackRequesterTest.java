package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.session.application.dto.GenerateFeedbackPayload;
import com.stackup.stackup.session.application.dto.GenerateFeedbackPayload.VoiceAnalysisSummary;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.MessageVoiceAnalysis;
import com.stackup.stackup.session.domain.MessageVoiceAnalysisRepository;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SessionFeedbackRequesterTest {

    @Mock RabbitMessagePublisher publisher;
    @Mock RabbitMqProperties properties;
    @Mock InterviewSessionRepository sessionRepository;
    @Mock InterviewMessageRepository messageRepository;
    @Mock SessionContextRepository contextRepository;
    @Mock SessionFeedbackRepository feedbackRepository;
    @Mock MessageVoiceAnalysisRepository voiceAnalysisRepository;
    @InjectMocks SessionFeedbackRequester requester;

    @Test
    void onSessionEnded_publishesGenerateFeedback() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage q = InterviewMessage.interviewer(session, 1, "Q");
        InterviewMessage a = InterviewMessage.interviewee(session, 2, "A", q, null);
        ReflectionTestUtils.setField(q, "id", 100L);
        ReflectionTestUtils.setField(a, "id", 101L);

        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(false);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySession_IdOrderBySequenceNumberAsc(50L)).thenReturn(List.of(q, a));
        when(contextRepository.findBySession_Id(50L)).thenReturn(List.of());
        when(voiceAnalysisRepository.findByMessage_Session_Id(50L)).thenReturn(List.of(
            MessageVoiceAnalysis.of(a, 120.0, 1.5, "{\"um\":2,\"uh\":1}", 0.91),
            MessageVoiceAnalysis.of(q, 180.0, 2.0, "{\"um\":3,\"like\":1}", 0.95)
        ));
        RabbitMqProperties.RoutingKeyProperties rk = mockRoutingKeys();
        when(properties.routingKeys()).thenReturn(rk);

        requester.onSessionEnded(new SessionEndedEvent(1L, 50L, "MAX_QUESTIONS_REACHED"));

        ArgumentCaptor<GenerateFeedbackPayload> payloadCaptor =
            ArgumentCaptor.forClass(GenerateFeedbackPayload.class);
        verify(publisher).publishToAi(eq("generate.feedback"), payloadCaptor.capture(), any(MessageContext.class));

        GenerateFeedbackPayload payload = payloadCaptor.getValue();
        assertThat(payload.messages()).hasSize(2);
        VoiceAnalysisSummary summary = payload.voiceAnalysisSummary();
        assertThat(summary.analyzedMessageCount()).isEqualTo(2);
        assertThat(summary.averageSpeakingRateWpm()).isEqualTo(150.0);
        assertThat(summary.totalSilenceDurationSec()).isEqualTo(3.5);
        assertThat(summary.fillerWordCounts()).containsAllEntriesOf(java.util.Map.of(
            "like", 1,
            "uh", 1,
            "um", 5
        ));
    }

    @Test
    @DisplayName("generate.feedback voice summary ignores malformed filler json but keeps aggregate metrics")
    void onSessionEnded_ignoresMalformedFillerJson() {
        InterviewSession session = sessionFixture(50L);
        InterviewMessage a = InterviewMessage.interviewee(session, 1, "A", null, null);
        ReflectionTestUtils.setField(a, "id", 101L);

        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(false);
        when(sessionRepository.findById(50L)).thenReturn(Optional.of(session));
        when(messageRepository.findBySession_IdOrderBySequenceNumberAsc(50L)).thenReturn(List.of(a));
        when(contextRepository.findBySession_Id(50L)).thenReturn(List.of());
        when(voiceAnalysisRepository.findByMessage_Session_Id(50L)).thenReturn(List.of(
            MessageVoiceAnalysis.of(a, null, null, "not-json", null)
        ));
        RabbitMqProperties.RoutingKeyProperties rk = mockRoutingKeys();
        when(properties.routingKeys()).thenReturn(rk);

        requester.onSessionEnded(new SessionEndedEvent(1L, 50L, "USER_REQUEST"));

        ArgumentCaptor<GenerateFeedbackPayload> payloadCaptor =
            ArgumentCaptor.forClass(GenerateFeedbackPayload.class);
        verify(publisher).publishToAi(eq("generate.feedback"), payloadCaptor.capture(), any(MessageContext.class));

        VoiceAnalysisSummary summary = payloadCaptor.getValue().voiceAnalysisSummary();
        assertThat(summary.analyzedMessageCount()).isEqualTo(1);
        assertThat(summary.averageSpeakingRateWpm()).isNull();
        assertThat(summary.totalSilenceDurationSec()).isEqualTo(0.0);
        assertThat(summary.fillerWordCounts()).isEmpty();
    }

    @Test
    void onSessionEnded_skipsWhenFeedbackExists() {
        when(feedbackRepository.existsBySession_Id(50L)).thenReturn(true);

        requester.onSessionEnded(new SessionEndedEvent(1L, 50L, "USER_REQUEST"));

        verify(publisher, never()).publishToAi(any(), any(), any());
    }

    private InterviewSession sessionFixture(Long id) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        InterviewSession s = InterviewSession.create(user, "t", null, SessionMode.TECHNICAL, JobCategory.BACKEND, 5, 30);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    private RabbitMqProperties.RoutingKeyProperties mockRoutingKeys() {
        return new RabbitMqProperties.RoutingKeyProperties(
            "analyze.resume", "analyze.repository",
            "generate.questions", "generate.followup", "generate.feedback", "analyze.voice",
            "callback.analysis", "callback.questions", "callback.feedback", "callback.voice",
            "callback.tts",
            "realtime.session.notify", "realtime.user.notify", "realtime.document.notify");
    }
}
