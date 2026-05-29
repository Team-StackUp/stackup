package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.presentation.dto.SessionCreateRequest;
import com.stackup.stackup.session.presentation.dto.SessionResponse;
import com.stackup.stackup.session.presentation.dto.SessionUpdateRequest;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Sessions", description = "면접 세션 생성·조회·상태 전환. 컨텍스트(이력서/레포 분석 문서) 연결 포함.")
@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @Operation(
        operationId = "createSession",
        summary = "세션 생성 (US-13/14)",
        description = "mode/interviewType/jobCategory 필수. contextDocumentIds 로 분석 문서 N개 연결 (US-14). 분석 미완료 문서는 422."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "세션 생성 완료"),
        @ApiResponse(responseCode = "400", description = "요청 검증 실패"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "지정된 문서 없음"),
        @ApiResponse(responseCode = "422", description = "분석 미완료 문서를 컨텍스트로 지정")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SessionCreateRequest request
    ) {
        return SessionResponse.from(sessionService.create(principal.userId(), request.toCommand()));
    }

    @Operation(operationId = "listSessions", summary = "내 세션 히스토리 (US-15)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "세션 목록"),
        @ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @GetMapping
    public List<SessionResponse> list(@AuthenticationPrincipal UserPrincipal principal) {
        return sessionService.list(principal.userId()).stream()
            .map(SessionResponse::from)
            .toList();
    }

    @Operation(operationId = "getSession", summary = "세션 상세 (US-16)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "세션 상세"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @GetMapping("/{sessionId}")
    public SessionResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return SessionResponse.from(sessionService.get(principal.userId(), sessionId));
    }

    @Operation(operationId = "updateSessionMeta", summary = "세션 제목/메모 수정")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "갱신 완료"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @PatchMapping("/{sessionId}")
    public SessionResponse update(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @Valid @RequestBody SessionUpdateRequest request
    ) {
        return SessionResponse.from(sessionService.updateMeta(
            principal.userId(), sessionId, request.title(), request.memo()
        ));
    }

    @Operation(operationId = "startSession", summary = "세션 시작 (READY→IN_PROGRESS) (US-17)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "시작됨"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "READY 아님")
    })
    @PatchMapping("/{sessionId}/start")
    public SessionResponse start(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return SessionResponse.from(sessionService.start(principal.userId(), sessionId));
    }

    @Operation(operationId = "endSession", summary = "세션 종료 (IN_PROGRESS→COMPLETED) (US-17)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "종료됨"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "IN_PROGRESS 아님")
    })
    @PatchMapping("/{sessionId}/end")
    public SessionResponse end(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return SessionResponse.from(sessionService.end(principal.userId(), sessionId));
    }

    @Operation(operationId = "interruptSession", summary = "세션 중단 (IN_PROGRESS→INTERRUPTED) (US-17)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "중단됨"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "IN_PROGRESS 아님")
    })
    @PatchMapping("/{sessionId}/interrupt")
    public SessionResponse interrupt(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return SessionResponse.from(sessionService.interrupt(principal.userId(), sessionId));
    }

    @Operation(operationId = "cancelSession", summary = "세션 취소 (READY→CANCELLED)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "취소됨"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음"),
        @ApiResponse(responseCode = "422", description = "READY 아님")
    })
    @PatchMapping("/{sessionId}/cancel")
    public SessionResponse cancel(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return SessionResponse.from(sessionService.cancel(principal.userId(), sessionId));
    }

    @Operation(operationId = "deleteSession", summary = "세션 soft delete")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "삭제됨"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음")
    })
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        sessionService.delete(principal.userId(), sessionId);
    }
}
