package com.stackup.stackup.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.auth.application.dto.GithubUserProfile;
import com.stackup.stackup.auth.application.dto.GithubUserUpsertResult;
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
class GithubUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void upsertGithubUser_createsNewUser() {
        GithubUserService service = new GithubUserService(userRepository);
        GithubUserProfile profile = new GithubUserProfile(123L, "octocat", "octocat@example.com", "avatar");
        when(userRepository.findByGithubIdAndDeletedFalse(123L)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });

        GithubUserUpsertResult result = service.upsertGithubUser(profile, "encrypted-token");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getGithubId()).isEqualTo(123L);
        assertThat(savedUser.getGithubUsername()).isEqualTo("octocat");
        assertThat(savedUser.getEncryptedGithubAccessToken()).isEqualTo("encrypted-token");
        assertThat(result.newUser()).isTrue();
    }

    @Test
    void upsertGithubUser_updatesExistingUser() {
        GithubUserService service = new GithubUserService(userRepository);
        User user = User.createGithubUser(123L, "old", null, null, "old-token");
        ReflectionTestUtils.setField(user, "id", 1L);
        GithubUserProfile profile = new GithubUserProfile(123L, "octocat", "octocat@example.com", "avatar");
        when(userRepository.findByGithubIdAndDeletedFalse(123L)).thenReturn(Optional.of(user));

        GithubUserUpsertResult result = service.upsertGithubUser(profile, "encrypted-token");

        assertThat(user.getGithubUsername()).isEqualTo("octocat");
        assertThat(user.getEmail()).isEqualTo("octocat@example.com");
        assertThat(user.getAvatarUrl()).isEqualTo("avatar");
        assertThat(user.getEncryptedGithubAccessToken()).isEqualTo("encrypted-token");
        assertThat(result.newUser()).isFalse();
    }
}
