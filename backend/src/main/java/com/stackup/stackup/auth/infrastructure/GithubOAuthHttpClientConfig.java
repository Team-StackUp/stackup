package com.stackup.stackup.auth.infrastructure;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class GithubOAuthHttpClientConfig {

    @Bean
    public GithubOAuthHttpClient githubOAuthHttpClient(GithubOAuthProperties properties) {
        RestClient restClient = RestClient.builder()
            .baseUrl(properties.oauthBaseUrl().toString())
            .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(GithubOAuthHttpClient.class);
    }
}
