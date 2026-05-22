package com.stackup.stackup.github.presentation;

import com.stackup.stackup.github.application.InternalGithubTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// AI 서버 전용 
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
