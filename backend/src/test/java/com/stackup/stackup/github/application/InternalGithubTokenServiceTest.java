package com.stackup.stackup.github.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AI 서버가 레포 분석 시점에 위임받는 GitHub access token 은 `repo` 스코프다 —
 * 비공개 레포까지 읽을 수 있는 살아있는 자격증명이라 위임 조건이 좁아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class InternalGithubTokenServiceTest {

    @Mock UserRepository userRepository;
    @Mock GithubTokenCipher tokenCipher;
    @InjectMocks InternalGithubTokenService service;

    @Test
    void returnsDecryptedTokenForActiveUser() {
        User user = User.createGithubUser(1L, "u", null, null, "enc");
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        when(tokenCipher.decrypt("enc")).thenReturn("gho_plain");

        assertThat(service.fetchPlainAccessToken(1L)).isEqualTo("gho_plain");
    }

    // 탈퇴한 계정은 삭제 여부에서 먼저 막는다. 토큰이 비어 있어서 막히는 것에만 기대면
    // 백필 전 데이터·향후 실수에 그대로 뚫린다.
    @Test
    void refusesWithdrawnUserEvenIfTokenRowStillPresent() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.fetchPlainAccessToken(1L))
            .isInstanceOf(DomainException.class)
            .extracting(e -> ((DomainException) e).getErrorCode())
            .isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    // Google 로 가입한 계정은 GitHub 토큰이 없다 — NPE 가 500 으로 새지 않게 도메인 에러로.
    @Test
    void refusesGoogleOnlyAccountWithDomainError() {
        User google = User.createGoogleUser("g-1", "u", null, null);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(google));

        assertThatThrownBy(() -> service.fetchPlainAccessToken(1L))
            .isInstanceOf(DomainException.class)
            .extracting(e -> ((DomainException) e).getErrorCode())
            .isEqualTo(ApiErrorCode.AUTH_GITHUB_NOT_LINKED);
    }
}
