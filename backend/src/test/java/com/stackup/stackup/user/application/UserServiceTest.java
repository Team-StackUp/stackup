package com.stackup.stackup.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.user.application.dto.UserProfileResult;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void getCurrentUser_returnsAuthenticatedUserProfile() {
        User user = User.createGithubUser(
            123L,
            "octocat",
            "octocat@example.com",
            "https://avatars.githubusercontent.com/u/123456",
            "encrypted-token"
        );
        ReflectionTestUtils.setField(user, "id", 1L);
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(user));
        UserService service = new UserService(userRepository);

        UserProfileResult result = service.getCurrentUser(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.githubId()).isEqualTo(123L);
        assertThat(result.githubUsername()).isEqualTo("octocat");
        assertThat(result.email()).isEqualTo("octocat@example.com");
        assertThat(result.avatarUrl()).isEqualTo("https://avatars.githubusercontent.com/u/123456");
    }

    @Test
    void getCurrentUser_rejectsMissingPrincipal() {
        UserService service = new UserService(userRepository);

        assertThatThrownBy(() -> service.getCurrentUser(null))
            .isInstanceOf(DomainException.class)
            .extracting("errorCode")
            .isEqualTo(ApiErrorCode.AUTH_INVALID_TOKEN);
    }

    @Test
    void getCurrentUser_rejectsMissingUser() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());
        UserService service = new UserService(userRepository);

        assertThatThrownBy(() -> service.getCurrentUser(1L))
            .isInstanceOf(DomainException.class)
            .extracting("errorCode")
            .isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }
}
