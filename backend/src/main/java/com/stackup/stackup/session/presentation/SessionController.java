package com.stackup.stackup.session.presentation;

import com.stackup.stackup.common.security.UserPrincipal;
import com.stackup.stackup.session.application.SessionQueryService;
import com.stackup.stackup.session.application.SessionService;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.presentation.dto.SessionCreateRequest;
import com.stackup.stackup.session.presentation.dto.SessionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
