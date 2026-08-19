package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.response.PageResponse;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.presentation.dto.SessionCreateRequest;
import com.stackup.stackup.session.presentation.dto.SessionResponse;
import com.stackup.stackup.session.presentation.dto.SessionRetryRequest;
import com.stackup.stackup.session.presentation.dto.SessionUpdateRequest;
import com.stackup.stackup.session.presentation.dto.StreamTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
        description = "mode/jobCategory 필수. contextDocumentIds 로 분석 문서 N개 연결 (US-14). 분석 미완료 문서는 422."
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
    public PageResponse<SessionResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC)
        Pageable pageable
    ) {
        return PageResponse.from(
            sessionService.listPaged(principal.userId(), pageable).map(SessionResponse::from)
        );
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

    @Operation(
        operationId = "retrySession",
        summary = "같은 설정으로 다시 면접 (US-13 재도전)",
        description = "기존 세션의 설정(모드·직군·질문 수·JD 등)을 그대로 복사해 새 세션을 만든다. "
            + "focusOnWeakness=true 면 원본 피드백에서 낮았던 평가 축을 집중 영역으로 새겨, 질문 생성이 "
            + "그 영역을 검증하는 질문을 우선 배치한다. "
            + "연결 자료는 지금도 살아있고 분석이 끝난 것만 다시 잇는다 — 그 사이 삭제된 자료 때문에 "
            + "재도전 전체가 404 로 막히지 않게 하기 위함. 응답의 contextDocumentIds 를 원본과 비교하면 "
            + "무엇이 빠졌는지 알 수 있다."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "새 세션 생성 완료"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "원본 세션 없음")
    })
    @PostMapping("/{sessionId}/retry")
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse retry(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @RequestBody(required = false) SessionRetryRequest request
    ) {
        boolean focusOnWeakness = request != null && request.focusOnWeaknessOrDefault();
        return SessionResponse.from(
            sessionService.retry(principal.userId(), sessionId, focusOnWeakness));
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

    @Operation(operationId = "createSessionStreamToken", summary = "세션 채널 SSE/WS 용 리소스 스코프 stream token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "토큰 발급"),
        @ApiResponse(responseCode = "401", description = "인증 실패"),
        @ApiResponse(responseCode = "404", description = "세션 없음 / 소유자 아님")
    })
    @PostMapping("/{sessionId}/stream-token")
    public StreamTokenResponse createSessionStreamToken(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return new StreamTokenResponse(
            sessionService.createSessionStreamToken(principal.userId(), sessionId));
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
