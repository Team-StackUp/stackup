package com.stackup.stackup.github.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.github.application.GithubRepositoryService;
import com.stackup.stackup.github.presentation.dto.CandidateRepositoryResponse;
import com.stackup.stackup.github.presentation.dto.RegisterRepositoryRequest;
import com.stackup.stackup.github.presentation.dto.RegisteredRepositoryResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class GithubRepositoryController {

    private final GithubRepositoryService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredRepositoryResponse register(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody RegisterRepositoryRequest request
    ) {
        return RegisteredRepositoryResponse.from(service.register(principal.userId(), request.toCommand()));
    }

    @GetMapping
    public List<RegisteredRepositoryResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return service.listRegistered(principal.userId()).stream()
            .map(RegisteredRepositoryResponse::from)
            .toList();
    }

    @GetMapping("/github")
    public List<CandidateRepositoryResponse> listCandidates(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(name = "perPage", defaultValue = "30") int perPage
    ) {
        return service.listCandidates(principal.userId(), page, perPage).stream()
            .map(CandidateRepositoryResponse::from)
            .toList();
    }

    @GetMapping("/{repositoryId}")
    public RegisteredRepositoryResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long repositoryId
    ) {
        return RegisteredRepositoryResponse.from(service.get(principal.userId(), repositoryId));
    }

    @DeleteMapping("/{repositoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long repositoryId
    ) {
        service.delete(principal.userId(), repositoryId);
    }
}
