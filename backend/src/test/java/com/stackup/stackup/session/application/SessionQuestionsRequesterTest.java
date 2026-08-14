package com.stackup.stackup.session.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionQuestionsRequesterTest {

    @Mock private RabbitMessagePublisher publisher;
    @Mock private RabbitMqProperties properties;
    @Mock private AnalyzedDocumentRepository documentRepository;
    @Mock private ObjectStorageClient storage;
    @Mock private InterviewSessionRepository sessionRepository;
    @Mock private SessionQuestionPoolRepository questionPoolRepository;
    @Mock private QuestionsCallbackService questionsCallbackService;

    @InjectMocks private SessionQuestionsRequester requester;

    private SelfIntroAnsweredEvent event(Integer generalQuestionCount) {
        return new SelfIntroAnsweredEvent(
            7L, 99L, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            10, generalQuestionCount, List.of(), "자기소개 답변입니다.", null, null
        );
    }

    @Test
    void generalCountOne_endsAfterSelfIntroInsteadOfAskingOneMore() {
        // 자기소개가 유일한 일반질문 자리를 다 쓴 경우. 예전엔 Math.max(1, count-1) 로
        // 풀을 1개 강제 생성해서, 1을 고른 사용자가 질문 2개를 받았다.
        requester.onSelfIntroAnswered(event(1));

        verify(publisher, never()).publishToAi(anyString(), any(), any(MessageContext.class));
        // 발행을 그냥 건너뛰면 콜백이 없어 세션이 멈춘다 — 풀 고갈과 같은 종료 경로를 타야 한다.
        verify(questionsCallbackService).advanceToNextGeneral(99L);
    }

    @Test
    void generalCountThree_requestsPoolOfTwo() {
        when(properties.routingKeys()).thenReturn(routingKeys());

        requester.onSelfIntroAnswered(event(3));

        verify(questionsCallbackService, never()).advanceToNextGeneral(eq(99L));
        verify(publisher).publishToAi(eq("generate.questions"), any(), any(MessageContext.class));
    }

    // record 라 mock 이 안 된다 — 실제 인스턴스를 만든다. 이 테스트가 보는 건 generateQuestions 뿐.
    private RabbitMqProperties.RoutingKeyProperties routingKeys() {
        return new RabbitMqProperties.RoutingKeyProperties(
            "x", "x", "x", "generate.questions", "x", "x", "x", "x",
            "x", "x", "x", "x", "x", "x", "x", "x"
        );
    }
}
