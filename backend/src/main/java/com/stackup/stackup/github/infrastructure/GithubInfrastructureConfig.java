package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class GithubInfrastructureConfig {

    @Bean
    public GithubApi githubApi(GithubOAuthProperties properties) {
        return githubApi(properties, RestClient.builder());
    }

    GithubApi githubApi(GithubOAuthProperties properties, RestClient.Builder restClientBuilder) {
        RestClient restClient = restClientBuilder
            .baseUrl(properties.apiBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", properties.apiVersion())
            .build();
        return HttpServiceProxyFactory
            .builderFor(RestClientAdapter.create(restClient))
            .build()
            .createClient(GithubApi.class);
    }
}
