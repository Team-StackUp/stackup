package com.stackup.stackup.github.infrastructure;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class GithubApiHttpClientConfig {

    @Bean
    public GithubApiHttpClient githubApiHttpClient(GithubOAuthProperties properties) {
        RestClient restClient = RestClient.builder()
            .requestFactory(requestFactory(properties))
            .baseUrl(properties.apiBaseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .defaultHeader("X-GitHub-Api-Version", properties.apiVersion())
            .build();
        RestClientAdapter adapter = RestClientAdapter.create(restClient);
        HttpServiceProxyFactory factory = HttpServiceProxyFactory.builderFor(adapter).build();
        return factory.createClient(GithubApiHttpClient.class);
    }

    private SimpleClientHttpRequestFactory requestFactory(GithubOAuthProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return requestFactory;
    }
}
