package com.stackup.stackup.github.presentation;

import com.stackup.stackup.github.application.InternalGithubTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 서버 전용. /api/internal/users/{userId}/github-token.
 * URL prefix 는 users 도메인이지만 토큰 처리는 github 도메인 책임이라 본 패키지에 둔다.
 */
@RestController
@RequestMapping("/api/internal/users")
@RequiredArgsConstructor
public class InternalGithubTokenController {

    private final InternalGithubTokenService tokenService;

    @GetMapping("/{userId}/github-token")
    public GithubTokenResponse fetchGithubToken(@PathVariable Long userId) {
        return new GithubTokenResponse(tokenService.fetchPlainAccessToken(userId));
    }

    public record GithubTokenResponse(String accessToken) {
    }
}
