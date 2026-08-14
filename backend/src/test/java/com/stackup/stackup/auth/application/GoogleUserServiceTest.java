package com.stackup.stackup.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.auth.application.dto.GoogleUserProfile;
import com.stackup.stackup.auth.application.dto.OAuthUserUpsertResult;
import com.stackup.stackup.user.domain.OAuthProvider;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class GoogleUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void upsertGoogleUser_createsUserWithoutGithubFields() {
        GoogleUserService service = new GoogleUserService(userRepository);
        GoogleUserProfile profile = new GoogleUserProfile("google-sub-1", "홍길동", "hong@example.com", "avatar");
        when(userRepository.findByGoogleIdAndDeletedFalse("google-sub-1")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 7L);
            return user;
        });

        OAuthUserUpsertResult result = service.upsertGoogleUser(profile);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();

        assertThat(saved.getProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(saved.getGoogleId()).isEqualTo("google-sub-1");
        assertThat(saved.getDisplayName()).isEqualTo("홍길동");
        // GitHub 연동 필드는 비어 있어야 한다 — 레포 기능이 이 값으로 분기한다.
        assertThat(saved.getGithubId()).isNull();
        assertThat(saved.getGithubUsername()).isNull();
        assertThat(saved.getEncryptedGithubAccessToken()).isNull();
        assertThat(saved.hasGithubLink()).isFalse();

        assertThat(result.newUser()).isTrue();
        assertThat(result.user().provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(result.user().displayName()).isEqualTo("홍길동");
        assertThat(result.user().githubUsername()).isNull();
    }

    @Test
    void upsertGoogleUser_updatesExistingUserWithoutInsert() {
        GoogleUserService service = new GoogleUserService(userRepository);
        User existing = User.createGoogleUser("google-sub-1", "옛이름", "old@example.com", "old-avatar");
        ReflectionTestUtils.setField(existing, "id", 7L);
        when(userRepository.findByGoogleIdAndDeletedFalse("google-sub-1")).thenReturn(Optional.of(existing));

        OAuthUserUpsertResult result = service.upsertGoogleUser(
            new GoogleUserProfile("google-sub-1", "새이름", "new@example.com", "new-avatar")
        );

        verify(userRepository, never()).save(any(User.class));
        assertThat(result.newUser()).isFalse();
        assertThat(existing.getDisplayName()).isEqualTo("새이름");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getAvatarUrl()).isEqualTo("new-avatar");
    }

    @Test
    void githubUser_keepsGithubLink() {
        User githubUser = User.createGithubUser(123L, "octocat", "octocat@example.com", "avatar", "encrypted");

        assertThat(githubUser.getProvider()).isEqualTo(OAuthProvider.GITHUB);
        // 기존 계정은 display_name 이 없던 시절에 만들어졌다 — 생성 시 login 으로 채워야 화면이 빈다.
        assertThat(githubUser.getDisplayName()).isEqualTo("octocat");
        assertThat(githubUser.getGoogleId()).isNull();
        assertThat(githubUser.hasGithubLink()).isTrue();
    }
}
