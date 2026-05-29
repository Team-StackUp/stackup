package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.response.PageResponse;
import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionQueryService;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.MessageSubmitCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.presentation.dto.AnswerSubmitRequest;
import com.stackup.stackup.session.presentation.dto.InterviewMessageResponse;
import com.stackup.stackup.session.presentation.dto.SessionCreateRequest;
import com.stackup.stackup.session.presentation.dto.SessionEndRequest;
import com.stackup.stackup.session.presentation.dto.SessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final SessionQueryService queryService; // 후속 Task 에서 구현

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(
        @AuthenticationPrincipal UserPrincipal principal,
        @Valid @RequestBody SessionCreateRequest request
    ) {
        SessionResult result = sessionService.create(request.toCommand(principal.userId()));
        return SessionResponse.from(result);
    }

    @PostMapping("/{sessionId}/end")
    public SessionResponse end(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @RequestBody(required = false) SessionEndRequest request
    ) {
        boolean cancel = request != null && request.isCancel();
        return SessionResponse.from(sessionService.end(sessionId, principal.userId(), cancel));
    }

    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public InterviewMessageResponse submitAnswer(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody AnswerSubmitRequest request
    ) {
        MessageResult r = sessionService.submitAnswer(new MessageSubmitCommand(
            sessionId, principal.userId(), request.content(), idempotencyKey));
        return InterviewMessageResponse.from(r);
    }

    @GetMapping("/{sessionId}")
    public SessionResponse get(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return SessionResponse.from(queryService.get(sessionId, principal.userId()));
    }

    @GetMapping
    public PageResponse<SessionResponse> list(
        @AuthenticationPrincipal UserPrincipal principal,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<SessionResult> page = queryService.list(principal.userId(), pageable);
        return PageResponse.from(page.map(SessionResponse::from));
    }

    @GetMapping("/{sessionId}/messages")
    public List<InterviewMessageResponse> getMessages(
        @AuthenticationPrincipal UserPrincipal principal,
        @PathVariable Long sessionId
    ) {
        return queryService.getMessages(sessionId, principal.userId())
            .stream().map(InterviewMessageResponse::from).toList();
    }
}
