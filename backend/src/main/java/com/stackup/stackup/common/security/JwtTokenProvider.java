package com.stackup.stackup.common.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.stackup.stackup.common.config.properties.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String USER_ID_CLAIM = "userId";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";

    private final SecurityProperties securityProperties;
    private final byte[] secretKey;

    public JwtTokenProvider(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
        this.secretKey = sha256(securityProperties.jwtSecret());
    }

    public String createAccessToken(Long userId) {
        Instant now = Instant.now();
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .subject(String.valueOf(userId))
            .claim(USER_ID_CLAIM, userId)
            .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(securityProperties.accessTokenTtlSeconds())))
            .build();

        return sign(claimsSet);
    }

    public JWTClaimsSet parseAndValidate(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new MACVerifier(secretKey))) {
                throw invalidToken();
            }

            JWTClaimsSet claimsSet = signedJwt.getJWTClaimsSet();
            if (claimsSet.getExpirationTime() == null || claimsSet.getExpirationTime().before(new Date())) {
                throw invalidToken();
            }
            if (!ACCESS_TOKEN_TYPE.equals(claimsSet.getStringClaim(TOKEN_TYPE_CLAIM))) {
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

    public Long getUserId(String token) {
        try {
            return parseAndValidate(token).getLongClaim(USER_ID_CLAIM);
        } catch (ParseException exception) {
            throw invalidToken();
        }
    }

    private String sign(JWTClaimsSet claimsSet) {
        try {
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claimsSet);
            signedJwt.sign(new MACSigner(secretKey));
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Failed to create JWT", exception);
        }
    }

    private BadCredentialsException invalidToken() {
        return new BadCredentialsException("Invalid JWT token");
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
