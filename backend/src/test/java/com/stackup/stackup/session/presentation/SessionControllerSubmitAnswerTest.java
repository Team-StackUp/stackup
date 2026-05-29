package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionQueryService;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SessionControllerSubmitAnswerTest {

    @Mock
    SessionService service;

    @Mock
    SessionQueryService queryService;

    MockMvc mvc;

    private static final UserPrincipal TEST_PRINCIPAL =
        new UserPrincipal(1L, "octocat", List.of());

    @BeforeEach
    void setUp() {
        SessionController controller = new SessionController(service, queryService);
        mvc = MockMvcBuilders
            .standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(TEST_PRINCIPAL, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void post_message_with_idempotency_key_returns_201() throws Exception {
        MessageResult r = new MessageResult(502L, 99L, 2, MessageRole.INTERVIEWEE,
            "A", 501L, MessageStatus.COMPLETED, Instant.now());
        when(service.submitAnswer(any())).thenReturn(r);

        mvc.perform(post("/api/sessions/99/messages")
                .header("Idempotency-Key", "uuid-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"내 답변\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(502))
            .andExpect(jsonPath("$.role").value("INTERVIEWEE"));
    }

    @Test
    void post_message_blank_content_returns_400() throws Exception {
        mvc.perform(post("/api/sessions/99/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
