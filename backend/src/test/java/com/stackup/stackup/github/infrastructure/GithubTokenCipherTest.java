package com.stackup.stackup.github.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GithubTokenCipherTest {

    @Test
    void encryptAndDecrypt() {
        GithubTokenCipher cipher = new GithubTokenCipher(securityProperties(validEncryptionKey()));

        String encrypted = cipher.encrypt("github-access-token");
        String decrypted = cipher.decrypt(encrypted);

        assertThat(encrypted).isNotEqualTo("github-access-token");
        assertThat(decrypted).isEqualTo("github-access-token");
    }

    @Test
    void encryptRejectsInvalidKey() {
        GithubTokenCipher cipher = new GithubTokenCipher(securityProperties("invalid-key"));

        assertThatThrownBy(() -> cipher.encrypt("github-access-token"))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED)
            );
    }

    private static SecurityProperties securityProperties(String encryptionKey) {
        return new SecurityProperties(
            "test-jwt-secret",
            encryptionKey,
            900,
            1209600
        );
    }

    private static String validEncryptionKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
