package com.stackup.stackup.common.sse;

import com.stackup.stackup.common.security.UserPrincipal;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
public record SseController(SseEmitterRegistry registry) {

    @GetMapping(value = "/me", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMe(@AuthenticationPrincipal UserPrincipal principal) {
        UserPrincipal authenticatedPrincipal = Objects.requireNonNull(principal, "authenticated principal is required");
        return registry.registerUser(authenticatedPrincipal.userId());
    }

    @GetMapping(value = "/sessions/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSession(@PathVariable Long sessionId) {
        return registry.registerSession(sessionId);
    }

    @GetMapping(value = "/documents/{documentId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamDocument(@PathVariable Long documentId) {
        return registry.registerDocument(documentId);
    }
}
