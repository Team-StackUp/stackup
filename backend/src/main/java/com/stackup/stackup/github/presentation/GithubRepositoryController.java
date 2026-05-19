package com.stackup.stackup.github.presentation;

import com.stackup.stackup.common.response.PageResponse;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.github.application.GithubRepositoryService;
import com.stackup.stackup.github.application.dto.GithubRepositoryResult;
import com.stackup.stackup.github.application.dto.RegisterRepositoryCommand;
import com.stackup.stackup.github.presentation.dto.CandidateRepositoryPageResponse;
import com.stackup.stackup.github.presentation.dto.RegisterRepositoryRequest;
import com.stackup.stackup.github.presentation.dto.RegisteredRepositoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Repositories", description = "GitHub 레포지토리 등록 및 관리")
public class GithubRepositoryController {

    private final GithubRepositoryService service;

    public GithubRepositoryController(GithubRepositoryService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "GitHub 레포 등록")
    public ResponseEntity<RegisteredRepositoryResponse> register(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody RegisterRepositoryRequest request
    ) {
        GithubRepositoryResult result = service.register(
            principal.userId(),
            new RegisterRepositoryCommand(request.githubRepoId())
        );
        return ResponseEntity
            .created(URI.create("/api/repositories/" + result.id()))
            .body(RegisteredRepositoryResponse.from(result));
    }

    @GetMapping
    @Operation(summary = "내가 등록한 레포 목록")
    public PageResponse<RegisteredRepositoryResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(service.list(principal.userId(), pageable).map(RegisteredRepositoryResponse::from));
    }

    @GetMapping("/github")
    @Operation(summary = "GitHub 레포 후보 목록")
    public CandidateRepositoryPageResponse listCandidates(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "30") int perPage
    ) {
        int safePerPage = Math.min(Math.max(perPage, 1), 100);
        int safePage = Math.max(page, 1);
        return CandidateRepositoryPageResponse.from(
            service.listCandidates(principal.userId(), safePage, safePerPage)
        );
    }

    @GetMapping("/{id}")
    @Operation(summary = "등록 레포 단건")
    public RegisteredRepositoryResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        return RegisteredRepositoryResponse.from(service.get(principal.userId(), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "등록 레포 소프트 삭제")
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long id
    ) {
        service.delete(principal.userId(), id);
    }
}
