package com.stackup.stackup.github.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.github.infrastructure.GithubTokenCipher;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InternalGithubTokenService {

    private final UserRepository userRepository;
    private final GithubTokenCipher tokenCipher;

    public String fetchPlainAccessToken(Long userId) {
        // 탈퇴한 계정의 토큰은 위임하지 않는다. 탈퇴 시 토큰을 지우므로(User.withdraw) 보통은
        // hasGithubLink 에서 걸리지만, 그건 "값이 비어 있어서" 막히는 것이라 데이터 상태에
        // 기대는 방어다. 삭제 여부로 먼저 막아 상태와 무관하게 닫는다.
        User user = userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));
        // Google 로 가입한 계정은 GitHub 토큰이 없다. 그대로 복호화로 넘기면 NPE 가 500 으로
        // 새어나가므로, 무엇이 부족한지 말해 주는 도메인 에러로 바꾼다.
        if (!user.hasGithubLink()) {
            throw new DomainException(ApiErrorCode.AUTH_GITHUB_NOT_LINKED);
        }
        return tokenCipher.decrypt(user.getEncryptedGithubAccessToken());
    }
}
