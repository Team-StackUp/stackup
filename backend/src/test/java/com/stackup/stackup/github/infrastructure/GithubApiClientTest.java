package com.stackup.stackup.github.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.stackup.stackup.common.config.properties.GithubOAuthProperties;
import com.stackup.stackup.github.infrastructure.dto.GithubRepoListPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GithubApiClientTest {

    private MockRestServiceServer server;
    private GithubApiClient client;

    @BeforeEach
    void setUp() {
        GithubOAuthProperties props = Mockito.mock(GithubOAuthProperties.class);
        when(props.apiBaseUrl()).thenReturn("https://api.github.com");
        when(props.apiVersion()).thenReturn("2022-11-28");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GithubApiClient(props, builder);
    }

    @Test
    void list_user_repositories_parses_link_header_and_returns_repos() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Link",
            "<https://api.github.com/user/repos?page=2>; rel=\"next\", " +
            "<https://api.github.com/user/repos?page=5>; rel=\"last\"");
        String body = """
            [{"id":1,"name":"hello","full_name":"o/hello","html_url":"https://github.com/o/hello",
              "default_branch":"main","private":false,"description":"d"}]""";

        server.expect(requestTo("https://api.github.com/user/repos?per_page=30&page=1&sort=updated&affiliation=owner,collaborator,organization_member"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess(body, MediaType.APPLICATION_JSON).headers(headers));

        GithubRepoListPage page = client.listUserRepositories("ghp_token", 1, 30);

        assertThat(page.repos()).hasSize(1);
        assertThat(page.repos().get(0).fullName()).isEqualTo("o/hello");
        assertThat(page.hasNext()).isTrue();

        server.verify();
    }

    @Test
    void list_user_repositories_hasNext_false_when_link_has_no_next() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Link", "<https://api.github.com/user/repos?page=1>; rel=\"prev\"");
        server.expect(requestTo("https://api.github.com/user/repos?per_page=30&page=2&sort=updated&affiliation=owner,collaborator,organization_member"))
              .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON).headers(headers));

        GithubRepoListPage page = client.listUserRepositories("tok", 2, 30);

        assertThat(page.hasNext()).isFalse();
    }
}
