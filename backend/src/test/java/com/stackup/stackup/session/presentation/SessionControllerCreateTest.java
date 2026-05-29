package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionQueryService;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.domain.InterviewType;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionStatus;
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
class SessionControllerCreateTest {

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

        // @AuthenticationPrincipal 이 SecurityContext 에서 principal 을 읽으므로 미리 세팅
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(TEST_PRINCIPAL, null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void post_creates_session_returns_201() throws Exception {
        SessionResult r = new SessionResult(
            99L, 1L, "T", null, SessionMode.ONLINE, InterviewType.TECHNICAL,
            JobCategory.BACKEND, 10, 60, SessionStatus.READY, 0, null, null, Instant.now());
        when(service.create(any())).thenReturn(r);

        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title":"T","mode":"ONLINE","interviewType":"TECHNICAL",
                     "jobCategory":"BACKEND","maxQuestions":10,"maxDurationMinutes":60,
                     "contextDocumentIds":[11,12]}"""))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(99))
            .andExpect(jsonPath("$.status").value("READY"));
    }

    @Test
    void post_validation_error_returns_400() throws Exception {
        mvc.perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"mode":"ONLINE","interviewType":"TECHNICAL","jobCategory":"BACKEND",
                     "maxQuestions":999}""")) // 30 초과
            .andExpect(status().isBadRequest());
    }
}
