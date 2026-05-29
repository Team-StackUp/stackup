package com.stackup.stackup.github.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.github.application.GithubRepositoryService;
import com.stackup.stackup.github.presentation.dto.CandidateRepositoryResponse;
import com.stackup.stackup.github.presentation.dto.RegisterRepositoryRequest;
import com.stackup.stackup.github.presentation.dto.RegisteredRepositoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Repositories", description = "GitHub 레포 등록/관리. 등록 시 분석 파이프라인이 자동 트리거되며 결과는 /api/stream/me (REPO_STATE) 로 통지됨.")
@RestController
@RequestMapping("/api/repositories")
@RequiredArgsConstructor
public class GithubRepositoryController {

    private final GithubRepositoryService service;

    @Operation(
        operationId = "registerRepository",
        summary = "GitHub 레포 등록 + 분석 트리거",
        description = "사용자의 GitHub 토큰으로 메타를 재확인 후 INSERT. id/fullName 불일치(VALIDATION_ERROR), 비공개 접근 불가(REPO_PRIVATE_NO_ACCESS), 존재하지 않음(REPO_NOT_FOUND), 중복(REPO_ALREADY_REGISTERED) 분기."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "등록 + 분석 트리거 성공"),
        @ApiResponse(responseCode = "400", description = "id ↔ fullName 불일치 / 잘못된 요청"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "403", description = "비공개 레포 접근 불가"),
        @ApiResponse(responseCode = "404", description = "GitHub에 해당 레포 없음"),
        @ApiResponse(responseCode = "409", description = "이미 등록된 레포")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RegisteredRepositoryResponse register(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody RegisterRepositoryRequest request
    ) {
        return RegisteredRepositoryResponse.from(service.register(principal.userId(), request.toCommand()));
    }

    @Operation(operationId = "listRegisteredRepositories", summary = "등록된 레포 목록")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "사용자가 등록한 레포 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public List<RegisteredRepositoryResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return service.listRegistered(principal.userId()).stream()
            .map(RegisteredRepositoryResponse::from)
            .toList();
    }

    @Operation(
        operationId = "listCandidateRepositories",
        summary = "GitHub 후보 레포 목록 (페이지)",
        description = "사용자의 GitHub 토큰으로 owner 레포를 updated 순으로 페이지 조회. 각 항목에 alreadyRegistered 플래그."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "GitHub 후보 페이지"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "502", description = "GitHub API 호출 실패")
    })
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

    @Operation(operationId = "getRepository", summary = "등록 레포 단건 조회")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "레포 메타"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "레포 없음")
    })
    @GetMapping("/{repositoryId}")
    public RegisteredRepositoryResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long repositoryId
    ) {
        return RegisteredRepositoryResponse.from(service.get(principal.userId(), repositoryId));
    }

    @Operation(
        operationId = "syncRepository",
        summary = "GitHub 메타 재동기화 (US-08)",
        description = "GitHub API 로 default_branch/name/url 등을 다시 fetch 해 DB 갱신. last_synced_at 갱신. 분석은 재트리거하지 않음."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "메타 갱신 완료"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "등록된 레포 또는 GitHub 상 레포 없음"),
        @ApiResponse(responseCode = "403", description = "비공개 레포 접근 불가"),
        @ApiResponse(responseCode = "502", description = "GitHub API 호출 실패")
    })
    @PostMapping("/{repositoryId}/sync")
    public RegisteredRepositoryResponse sync(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long repositoryId
    ) {
        return RegisteredRepositoryResponse.from(service.sync(principal.userId(), repositoryId));
    }

    @Operation(operationId = "deleteRepository", summary = "등록 레포 soft delete")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제 완료"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "레포 없음")
    })
    @DeleteMapping("/{repositoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long repositoryId
    ) {
        service.delete(principal.userId(), repositoryId);
    }
}
