package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.GenerateQuestionsPayload;
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
        return event(generalQuestionCount, List.of());
    }

    private SelfIntroAnsweredEvent event(Integer generalQuestionCount, List<Long> contextDocumentIds) {
        return new SelfIntroAnsweredEvent(
            7L, 99L, SessionMode.TECHNICAL, List.of(JobCategory.BACKEND),
            10, generalQuestionCount, contextDocumentIds, "자기소개 답변입니다.", null, null
        );
    }

    @Test
    void deletedDocument_isExcludedFromQuestionContext() {
        // 세션 생성 시점엔 살아있던 이력서를, 자기소개 답변 사이 워크스페이스에서 삭제한 상태.
        // findActiveByIdAndOwner 는 소유자 검증 + 삭제 필터를 함께 하므로 빈 값을 반환한다.
        when(properties.routingKeys()).thenReturn(routingKeys());
        when(documentRepository.findActiveByIdAndOwner(42L, 7L)).thenReturn(java.util.Optional.empty());

        requester.onSelfIntroAnswered(event(3, List.of(42L)));

        ArgumentCaptor<GenerateQuestionsPayload> captor = ArgumentCaptor.forClass(GenerateQuestionsPayload.class);
        verify(publisher).publishToAi(eq("generate.questions"), captor.capture(), any(MessageContext.class));
        // 삭제된 문서의 요약·기술스택·원문이 AI 에 실려 나가면 안 된다 —
        // 사용자가 "삭제"를 눌렀는데도 계속 근거로 쓰이는 셈이라 삭제의 의미가 없어진다.
        assertThat(captor.getValue().documents()).isEmpty();
    }

    @Test
    void activeDocument_isIncludedInQuestionContext() {
        when(properties.routingKeys()).thenReturn(routingKeys());
        AnalyzedDocument doc = org.mockito.Mockito.mock(AnalyzedDocument.class);
        when(doc.getId()).thenReturn(42L);
        when(doc.getAnalysisStatus()).thenReturn(AnalysisStatus.ANALYZED);
        when(documentRepository.findActiveByIdAndOwner(42L, 7L)).thenReturn(java.util.Optional.of(doc));

        requester.onSelfIntroAnswered(event(3, List.of(42L)));

        ArgumentCaptor<GenerateQuestionsPayload> captor = ArgumentCaptor.forClass(GenerateQuestionsPayload.class);
        verify(publisher).publishToAi(eq("generate.questions"), captor.capture(), any(MessageContext.class));
        assertThat(captor.getValue().documents()).hasSize(1);
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
            "x", "x", "x", "x", "generate.questions", "x", "x", "x",
            "x", "x", "x", "x", "x", "x", "x", "x", "x"
        );
    }
}
