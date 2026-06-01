package com.stackup.stackup.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import com.stackup.stackup.common.config.properties.SseProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class StreamTokenProvider {

    private static final String USER_ID_CLAIM = "userId";
    private static final String SCOPE_CLAIM = "scope";
    private static final String SSE_CONNECT_SCOPE = "SSE_CONNECT";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String STREAM_TOKEN_TYPE = "STREAM";
    private static final String RESOURCE_TYPE_CLAIM = "resourceType";
    private static final String RESOURCE_ID_CLAIM = "resourceId";

    public record StreamTokenClaims(Long userId, String resourceType, Long resourceId) {
    }

    private final SseProperties sseProperties;
    private final byte[] secretKey;

    public StreamTokenProvider(SecurityProperties securityProperties, SseProperties sseProperties) {
        this.sseProperties = sseProperties;
        this.secretKey = sha256(securityProperties.jwtSecret());
    }

    public String createStreamToken(Long userId, String resourceType, Long resourceId) {
        Instant now = Instant.now();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(String.valueOf(userId))
            .claim(USER_ID_CLAIM, userId)
            .claim(TOKEN_TYPE_CLAIM, STREAM_TOKEN_TYPE)
            .claim(SCOPE_CLAIM, SSE_CONNECT_SCOPE)
            .claim(RESOURCE_TYPE_CLAIM, resourceType)
            .claim(RESOURCE_ID_CLAIM, resourceId)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(sseProperties.streamTokenTtl())))
            .build();
        return sign(claimsSet);
    }

    public StreamTokenClaims parseClaims(String token) {
        JWTClaimsSet claims = parseAndValidate(token);
        try {
            Long userId = claims.getLongClaim(USER_ID_CLAIM);
            String resourceType = claims.getStringClaim(RESOURCE_TYPE_CLAIM);
            Long resourceId = claims.getLongClaim(RESOURCE_ID_CLAIM);
            return new StreamTokenClaims(userId, resourceType, resourceId);
        } catch (java.text.ParseException exception) {
            throw invalidToken();
        }
    }

    public boolean validateStreamToken(String token) {
        parseAndValidate(token);
        return true;
    }

    public Long getUserId(String token) {
        try {
            return parseAndValidate(token).getLongClaim(USER_ID_CLAIM);
        } catch (ParseException exception) {
            throw invalidToken();
        }
    }

    private JWTClaimsSet parseAndValidate(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secretKey))) {
                throw invalidToken();
            }

            JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
            if (claimsSet.getExpirationTime() == null || claimsSet.getExpirationTime().before(new Date())) {
                throw invalidToken();
            }
            if (!STREAM_TOKEN_TYPE.equals(claimsSet.getStringClaim(TOKEN_TYPE_CLAIM))) {
                throw invalidToken();
            }
            if (!SSE_CONNECT_SCOPE.equals(claimsSet.getStringClaim(SCOPE_CLAIM))) {
                throw invalidToken();
            }
            if (claimsSet.getLongClaim(USER_ID_CLAIM) == null) {
                throw invalidToken();
            }

            return claimsSet;
        } catch (ParseException | JOSEException exception) {
            throw invalidToken();
        }
    }

    private String sign(JWTClaimsSet claimsSet) {
        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJwt.sign(new MACSigner(secretKey));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to create stream token", exception);
        }
    }

    private BadCredentialsException invalidToken() {
        return new BadCredentialsException("Invalid stream token");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
