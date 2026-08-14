package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.auth.infrastructure.dto.GoogleUserInfoResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;

public interface GoogleUserInfoHttpClient {

    @GetExchange(url = "/oauth2/v3/userinfo", accept = MediaType.APPLICATION_JSON_VALUE)
    GoogleUserInfoResponse getUserInfo(@RequestHeader("Authorization") String authorization);
}
