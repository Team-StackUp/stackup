package com.stackup.stackup.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.support.PostgresRepositoryTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 탈퇴하면 GitHub access token 을 남기지 않는다.
 *
 * <p>이 토큰은 `repo` 스코프로 발급된다 — 비공개 레포까지 읽을 수 있는 살아있는 자격증명이다.
 * 탈퇴는 soft delete 만 하고 hard delete 는 Phase 2 라, 지우지 않으면 "삭제해 달라"고 한
 * 사용자의 GitHub 접근 권한이 우리 DB 에 무기한 남는다.
 *
 * <p>DB 까지 내려가서 확인하는 이유: users 에 provider 별 식별자 CHECK 제약이 걸려 있어
 * (`ck_users_provider_identity`) 엔티티에서 null 로 만들어도 UPDATE 가 거부될 수 있다.
 * 메모리 상태만 보는 테스트로는 그걸 못 잡는다.
 */
@PostgresRepositoryTest
class UserWithdrawalTest {

    @Autowired UserRepository userRepository;
    @Autowired EntityManager em;

    @Test
    void withdrawClearsGithubAccessToken() {
        User user = userRepository.save(
            User.createGithubUser(98001L, "leaving-user", "u@example.com", null, "encrypted-token"));
        assertThat(user.hasGithubLink()).isTrue();

        user.withdraw();
        userRepository.save(user);
        em.flush();

        assertThat(user.getEncryptedGithubAccessToken()).isNull();
        // GitHub 연동 기능은 자격증명이 사라진 계정으로 인식해야 한다
        // (InternalGithubTokenService 가 NPE 대신 AUTH_GITHUB_NOT_LINKED 로 떨어진다).
        assertThat(user.hasGithubLink()).isFalse();
    }

    // 탈퇴해도 같은 GitHub 계정으로 다시 가입할 수 있어야 한다(docs/security.md §5.3).
    // 부분 유니크 인덱스(V3)가 살아있는 행끼리만 유일성을 강제하므로 가능하다.
    @Test
    void sameGithubAccountCanSignUpAgainAfterWithdrawal() {
        User first = userRepository.save(
            User.createGithubUser(98002L, "rejoiner", null, null, "token-1"));
        first.withdraw();
        userRepository.save(first);
        em.flush();

        User second = userRepository.save(
            User.createGithubUser(98002L, "rejoiner", null, null, "token-2"));
        em.flush();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(userRepository.findByGithubIdAndDeletedFalse(98002L))
            .get()
            .extracting(User::getId)
            .isEqualTo(second.getId());
    }
}
