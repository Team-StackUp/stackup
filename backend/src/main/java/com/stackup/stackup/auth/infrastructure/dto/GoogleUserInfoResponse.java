package com.stackup.stackup.auth.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Google OpenID Connect userinfo 응답.
 *
 * sub 은 Google 이 보장하는 불변 식별자다 — 이메일은 사용자가 바꿀 수 있으므로 계정 식별에
 * 쓰지 않는다.
 */
public record GoogleUserInfoResponse(
    String sub,
    String name,
    String email,

    @JsonProperty("email_verified")
    Boolean emailVerified,

    String picture
) {
}
