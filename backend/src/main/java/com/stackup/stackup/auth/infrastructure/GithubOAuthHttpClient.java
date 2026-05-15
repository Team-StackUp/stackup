package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.auth.infrastructure.dto.GithubTokenResponse;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.PostExchange;

public interface GithubOAuthHttpClient {

    @PostExchange(
        url = "/login/oauth/access_token",
        contentType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        accept = MediaType.APPLICATION_JSON_VALUE
    )
    GithubTokenResponse exchangeCode(@RequestBody MultiValueMap<String, String> body);
}
