package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.common.config.properties.GoogleOAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

// 토큰 발급(oauth2.googleapis.com)과 userinfo(www.googleapis.com)는 호스트가 달라 클라이언트를 나눈다.
@Configuration
public class GoogleOAuthHttpClientConfig {

    @Bean
    public GoogleOAuthHttpClient googleOAuthHttpClient(GoogleOAuthProperties properties) {
        return createClient(GoogleOAuthHttpClient.class, properties, properties.tokenBaseUrl().toString());
    }

    @Bean
    public GoogleUserInfoHttpClient googleUserInfoHttpClient(GoogleOAuthProperties properties) {
        return createClient(GoogleUserInfoHttpClient.class, properties, properties.userInfoBaseUrl().toString());
    }

    private <T> T createClient(Class<T> type, GoogleOAuthProperties properties, String baseUrl) {
        RestClient restClient = RestClient.builder()
            .requestFactory(requestFactory(properties))
            .baseUrl(baseUrl)
            .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        return HttpServiceProxyFactory.builderFor(adapter).build().createClient(type);
    }

    private SimpleClientHttpRequestFactory requestFactory(GoogleOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
