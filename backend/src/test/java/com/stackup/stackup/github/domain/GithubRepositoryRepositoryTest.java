package com.stackup.stackup.github.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    classes = GithubRepositoryRepositoryTest.TestConfig.class,
    webEnvironment = WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=",
    "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///testdb",
    "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration",
    "spring.jpa.open-in-view=false",
    "app.security.jwt-secret=test-secret",
    "app.security.encryption-key=test-encryption-key",
    "app.github.client-id=test",
    "app.github.client-secret=test",
    "app.github.redirect-uri=http://localhost"
})
@Transactional
class GithubRepositoryRepositoryTest {

    @Configuration
    @EnableAutoConfiguration(exclude = {
        RabbitAutoConfiguration.class,
        SecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class
    })
    @EntityScan({
        "com.stackup.stackup.user.domain",
        "com.stackup.stackup.auth.domain",
        "com.stackup.stackup.resume.domain",
        "com.stackup.stackup.github.domain",
        "com.stackup.stackup.document.domain",
        "com.stackup.stackup.session.domain",
        "com.stackup.stackup.log.activity.domain",
        "com.stackup.stackup.log.ai.domain"
    })
    @EnableJpaRepositories(basePackages = "com.stackup.stackup")
    static class TestConfig {}

    @Autowired GithubRepositoryRepository repoRepository;
    @Autowired UserRepository userRepository;

    @Test
    void page_finder_excludes_deleted_and_other_users() {
        User a = userRepository.save(User.createGithubUser(7001L, "alice", "a@x", null, "tok"));
        User b = userRepository.save(User.createGithubUser(7002L, "bob", "b@x", null, "tok"));
        repoRepository.save(GithubRepository.create(a, 1L, "r1", "a/r1", "https://x", "main"));
        GithubRepository del = repoRepository.save(GithubRepository.create(a, 2L, "r2", "a/r2", "https://x", "main"));
        del.softDelete();
        repoRepository.save(del);
        repoRepository.save(GithubRepository.create(b, 3L, "rb", "b/rb", "https://x", "main"));

        var page = repoRepository.findByUser_IdAndDeletedFalse(
            a.getId(), PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(GithubRepository::getGithubRepoId).containsExactly(1L);
    }

    @Test
    void unfiltered_finder_returns_deleted_row_for_resurrect() {
        User u = userRepository.save(User.createGithubUser(7003L, "u", "u@x", null, "tok"));
        GithubRepository repo = repoRepository.save(GithubRepository.create(u, 9L, "r", "u/r", "https://x", "main"));
        repo.softDelete();
        repoRepository.save(repo);

        var found = repoRepository.findByUser_IdAndGithubRepoId(u.getId(), 9L);

        assertThat(found).isPresent();
        assertThat(found.get().isDeleted()).isTrue();
    }

    @Test
    void already_registered_projection_returns_ids_in_use() {
        User u = userRepository.save(User.createGithubUser(7004L, "u4", "u4@x", null, "tok"));
        repoRepository.save(GithubRepository.create(u, 10L, "a", "u/a", "https://x", "main"));
        repoRepository.save(GithubRepository.create(u, 11L, "b", "u/b", "https://x", "main"));
        GithubRepository del = repoRepository.save(GithubRepository.create(u, 12L, "c", "u/c", "https://x", "main"));
        del.softDelete();
        repoRepository.save(del);

        List<Long> ids = repoRepository.findGithubRepoIdsByUser_IdAndDeletedFalseAndGithubRepoIdIn(
            u.getId(), Set.of(10L, 11L, 12L, 99L)
        );

        assertThat(ids).containsExactlyInAnyOrder(10L, 11L);
    }
}
