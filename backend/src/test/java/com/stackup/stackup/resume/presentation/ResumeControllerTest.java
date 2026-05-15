package com.stackup.stackup.resume.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.resume.application.ResumeService;
import com.stackup.stackup.resume.application.dto.ResumeResult;
import com.stackup.stackup.resume.domain.ResumeStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ResumeControllerTest {

    @Mock
    ResumeService resumeService;

    @InjectMocks
    ResumeController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver(),
                new PageableHandlerMethodArgumentResolver()
            )
            .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(long userId) {
        UserPrincipal principal = new UserPrincipal(userId, "alice", List.of());
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void upload_returns_201_with_location_header() throws Exception {
        authenticateAs(42L);
        MockMultipartFile file = new MockMultipartFile(
            "file", "resume.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F'}
        );
        ResumeResult result = new ResumeResult(
            7L, "resume.pdf", 4L, ResumeStatus.PENDING,
            Instant.parse("2026-05-15T10:00:00Z"), Instant.parse("2026-05-15T10:00:00Z")
        );
        when(resumeService.upload(eq(42L), any())).thenReturn(result);

        mockMvc.perform(multipart("/api/resumes")
                .file(file)
                .contentType(MediaType.MULTIPART_FORM_DATA))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/resumes/7"))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void list_returns_paged_response() throws Exception {
        authenticateAs(42L);
        ResumeResult r = new ResumeResult(1L, "a.pdf", 1L, ResumeStatus.ANALYZED,
            Instant.parse("2026-05-15T10:00:00Z"), Instant.parse("2026-05-15T10:00:00Z"));
        var page = new org.springframework.data.domain.PageImpl<>(java.util.List.of(r),
            org.springframework.data.domain.PageRequest.of(0, 20), 1);
        when(resumeService.list(eq(42L), any())).thenReturn(page);

        mockMvc.perform(get("/api/resumes?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void get_single_returns_resume() throws Exception {
        authenticateAs(42L);
        ResumeResult r = new ResumeResult(1L, "a.pdf", 1L, ResumeStatus.PENDING,
            Instant.now(), Instant.now());
        when(resumeService.get(42L, 1L)).thenReturn(r);

        mockMvc.perform(get("/api/resumes/1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.originalFilename").value("a.pdf"));
    }

    @Test
    void delete_returns_204() throws Exception {
        authenticateAs(42L);
        mockMvc.perform(delete("/api/resumes/1"))
            .andExpect(status().isNoContent());
        org.mockito.Mockito.verify(resumeService).delete(42L, 1L);
    }
}
