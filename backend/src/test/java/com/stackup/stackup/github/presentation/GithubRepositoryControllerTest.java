package com.stackup.stackup.github.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.exception.GlobalExceptionHandler;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.github.application.GithubRepositoryService;
import com.stackup.stackup.github.application.dto.CandidateRepositoryListResult;
import com.stackup.stackup.github.application.dto.CandidateRepositoryResult;
import com.stackup.stackup.github.application.dto.GithubRepositoryResult;
import com.stackup.stackup.github.domain.RepositoryStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class GithubRepositoryControllerTest {

    @Mock
    GithubRepositoryService service;

    @InjectMocks
    GithubRepositoryController controller;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
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

    private GithubRepositoryResult registered(long id, long githubRepoId, String fullName) {
        return new GithubRepositoryResult(
            id, githubRepoId, fullName.substring(fullName.indexOf('/') + 1),
            fullName, "https://github.com/" + fullName,
            "main", RepositoryStatus.PENDING, null,
            Instant.parse("2026-05-15T10:00:00Z"), Instant.parse("2026-05-15T10:00:00Z")
        );
    }

    @Test
    void register_returns_201_with_location_header() throws Exception {
        authenticateAs(42L);
        when(service.register(eq(42L), any())).thenReturn(registered(7L, 1296269L, "octocat/Hello-World"));

        mockMvc.perform(post("/api/repositories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"githubRepoId\": 1296269}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "/api/repositories/7"))
            .andExpect(jsonPath("$.id").value(7))
            .andExpect(jsonPath("$.repoFullName").value("octocat/Hello-World"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void register_returns_409_when_already_registered() throws Exception {
        authenticateAs(42L);
        when(service.register(eq(42L), any()))
            .thenThrow(new DomainException(ApiErrorCode.REPO_ALREADY_REGISTERED));

        mockMvc.perform(post("/api/repositories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"githubRepoId\": 1}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("REPO_ALREADY_REGISTERED"));
    }

    @Test
    void register_returns_403_when_private_no_access() throws Exception {
        authenticateAs(42L);
        when(service.register(eq(42L), any()))
            .thenThrow(new DomainException(ApiErrorCode.REPO_PRIVATE_NO_ACCESS));

        mockMvc.perform(post("/api/repositories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"githubRepoId\": 99}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("REPO_PRIVATE_NO_ACCESS"));
    }

    @Test
    void list_returns_paged_response() throws Exception {
        authenticateAs(42L);
        var r = registered(1L, 100L, "u/n");
        var page = new PageImpl<>(java.util.List.of(r), PageRequest.of(0, 20), 1);
        when(service.list(eq(42L), any())).thenReturn(page);

        mockMvc.perform(get("/api/repositories?page=0&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.first").value(true))
            .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void list_candidates_returns_with_hasNext() throws Exception {
        authenticateAs(42L);
        var c1 = new CandidateRepositoryResult(1L, "a", "u/a", "https://x/a", "main", false, "d", false);
        var c2 = new CandidateRepositoryResult(2L, "b", "u/b", "https://x/b", "main", true, "d", true);
        var listResult = new CandidateRepositoryListResult(java.util.List.of(c1, c2), 1, 30, true);
        when(service.listCandidates(eq(42L), eq(1), eq(30))).thenReturn(listResult);

        mockMvc.perform(get("/api/repositories/github?page=1&perPage=30"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].githubRepoId").value(1))
            .andExpect(jsonPath("$.content[0].alreadyRegistered").value(false))
            .andExpect(jsonPath("$.content[1].alreadyRegistered").value(true))
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.perPage").value(30))
            .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void get_single_returns_404_when_not_owner() throws Exception {
        authenticateAs(42L);
        when(service.get(42L, 999L)).thenThrow(new DomainException(ApiErrorCode.REPO_NOT_FOUND));

        mockMvc.perform(get("/api/repositories/999"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("REPO_NOT_FOUND"));
    }

    @Test
    void delete_returns_204() throws Exception {
        authenticateAs(42L);
        mockMvc.perform(delete("/api/repositories/1"))
            .andExpect(status().isNoContent());
        verify(service).delete(42L, 1L);
    }
}
