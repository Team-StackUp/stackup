package com.stackup.stackup.resume.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    classes = ResumeRepositoryTest.TestConfig.class,
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
class ResumeRepositoryTest {

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

    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;

    @Test
    void page_finder_excludes_deleted_and_other_users() {
        User a = userRepository.save(User.createGithubUser(1001L, "alice", "a@x", null, "encrypted-token-1"));
        User b = userRepository.save(User.createGithubUser(1002L, "bob", "b@x", null, "encrypted-token-2"));
        resumeRepository.save(Resume.create(a, "a1.pdf", "k1", 1L, ResumeFileType.PDF));
        Resume a2 = resumeRepository.save(Resume.create(a, "a2.pdf", "k2", 1L, ResumeFileType.PDF));
        a2.softDelete();
        resumeRepository.save(a2);
        resumeRepository.save(Resume.create(b, "b1.pdf", "k3", 1L, ResumeFileType.PDF));

        Page<Resume> page = resumeRepository.findByUser_IdAndDeletedFalse(
            a.getId(),
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Resume::getOriginalFilename).containsExactly("a1.pdf");
    }
}
