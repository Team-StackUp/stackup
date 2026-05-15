package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class GithubTokenCipher {

    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int AES_256_KEY_BYTES = 32;

    private final SecurityProperties securityProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public GithubTokenCipher(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public String encrypt(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw oauthFailed();
        }

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        try {
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainToken.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException exception) {
            throw oauthFailed(exception);
        }
    }

    public String decrypt(String encryptedToken) {
        if (encryptedToken == null || encryptedToken.isBlank()) {
            throw oauthFailed();
        }

        try {
            byte[] payload = Base64.getDecoder().decode(encryptedToken);
            if (payload.length <= IV_LENGTH_BYTES) {
                throw oauthFailed();
            }

            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw oauthFailed(exception);
        }
    }

    private SecretKeySpec secretKey() {
        try {
            byte[] key = Base64.getDecoder().decode(securityProperties.encryptionKey());
            if (key.length != AES_256_KEY_BYTES) {
                throw oauthFailed();
            }
            return new SecretKeySpec(key, KEY_ALGORITHM);
        } catch (IllegalArgumentException exception) {
            throw oauthFailed(exception);
        }
    }

    private DomainException oauthFailed() {
        return new DomainException(ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED);
    }

    private DomainException oauthFailed(Throwable cause) {
        return new DomainException(
            ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED,
            ApiErrorCode.AUTH_GITHUB_OAUTH_FAILED.getDefaultMessage(),
            cause
        );
    }
}
