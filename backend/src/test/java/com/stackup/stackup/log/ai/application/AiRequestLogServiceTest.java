package com.stackup.stackup.log.ai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.log.ai.application.dto.AiRequestLogCommand;
import com.stackup.stackup.log.ai.domain.AiRequestLog;
import com.stackup.stackup.log.ai.domain.AiRequestLogRepository;
import com.stackup.stackup.log.ai.domain.AiRequestStatus;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AiRequestLogServiceTest {

    @Mock AiRequestLogRepository logRepository;
    @Mock UserRepository userRepository;
    @Mock InterviewSessionRepository sessionRepository;
    @InjectMocks AiRequestLogService service;

    @Test
    void record_savesLogWithResolvedUserAndSession() {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sessionRepository.findById(10L)).thenReturn(Optional.empty());

        service.record(new AiRequestLogCommand(
            1L, 10L, "analyze.document", "gemini-3.1-pro",
            123, 456, 1500, "SUCCESS", null
        ));

        ArgumentCaptor<AiRequestLog> cap = ArgumentCaptor.forClass(AiRequestLog.class);
        verify(logRepository).save(cap.capture());
        AiRequestLog saved = cap.getValue();
        assertThat(saved.getStatus()).isEqualTo(AiRequestStatus.SUCCESS);
        assertThat(saved.getRequestType()).isEqualTo("analyze.document");
        assertThat(saved.getInputTokens()).isEqualTo(123);
    }

    @Test
    void record_fallsBackToFailedOnUnknownStatus() {
        service.record(new AiRequestLogCommand(
            null, null, "generate.followup", "gemini-flash",
            10, 20, 800, "BOGUS", null
        ));

        ArgumentCaptor<AiRequestLog> cap = ArgumentCaptor.forClass(AiRequestLog.class);
        verify(logRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(AiRequestStatus.FAILED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "generate.questions",
        "generate.followup",
        "generate.feedback",
        "stt.transcribe",
        "tts.synthesize"
    })
    void record_keepsAiWorkflowRequestType(String requestType) {
        service.record(new AiRequestLogCommand(
            null, null, requestType, "gemini-flash",
            10, 20, 800, "SUCCESS", null
        ));

        ArgumentCaptor<AiRequestLog> cap = ArgumentCaptor.forClass(AiRequestLog.class);
        verify(logRepository).save(cap.capture());
        assertThat(cap.getValue().getRequestType()).isEqualTo(requestType);
    }
}
