package com.stackup.stackup.session.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.stackup.stackup.session.application.VoiceAnswerUploadService.VoicePlaceholder;
import com.stackup.stackup.session.application.dto.VoiceStreamBeginResult;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VoiceStreamServiceTest {

    @Mock VoiceAnswerUploadService uploadService;
    @InjectMocks VoiceStreamService service;

    @Test
    void begin_createsPlaceholder_returnsIds() {
        InterviewSession session = org.mockito.Mockito.mock(InterviewSession.class);
        InterviewMessage placeholder = org.mockito.Mockito.mock(InterviewMessage.class);
        InterviewMessage parent = org.mockito.Mockito.mock(InterviewMessage.class);
        when(placeholder.getId()).thenReturn(50L);
        when(placeholder.getSequenceNumber()).thenReturn(4);
        when(parent.getId()).thenReturn(49L);
        when(uploadService.createVoicePlaceholder(1L, 7L, "idem-1"))
            .thenReturn(new VoicePlaceholder(session, placeholder, parent));

        VoiceStreamBeginResult result = service.begin(1L, 7L, "idem-1");

        assertThat(result.messageId()).isEqualTo(50L);
        assertThat(result.parentMessageId()).isEqualTo(49L);
        assertThat(result.sequenceNumber()).isEqualTo(4);
    }
}
