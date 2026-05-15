package com.stackup.stackup.document.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(
    classes = AnalyzedDocumentRepositoryTest.TestConfig.class,
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
class AnalyzedDocumentRepositoryTest {

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

    @Autowired AnalyzedDocumentRepository documentRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired UserRepository userRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void exists_active_session_context_returns_false_when_no_context() {
        User u = userRepository.save(User.createGithubUser(2001L, "u", "u@x", null, "tok"));
        Resume r = resumeRepository.save(Resume.create(u, "f.pdf", "k", 1L, ResumeFileType.PDF));

        assertThat(documentRepository.existsActiveSessionContextForResume(r.getId())).isFalse();
    }

    @Test
    void exists_active_session_context_returns_true_when_in_progress_session_references_resume() {
        User u = userRepository.save(User.createGithubUser(2002L, "v", "v@x", null, "tok"));
        Resume r = resumeRepository.save(Resume.create(u, "f.pdf", "k", 1L, ResumeFileType.PDF));

        Long docId = jdbcTemplate.queryForObject(
            "INSERT INTO analyzed_documents (resume_id, document_path, status, created_at, updated_at, is_deleted) " +
                "VALUES (?, 'analyzed/resume/x/summary.md', 'ACTIVE', NOW(), NOW(), FALSE) RETURNING id",
            Long.class, r.getId()
        );
        Long sessionId = jdbcTemplate.queryForObject(
            "INSERT INTO interview_sessions (user_id, mode, interview_type, job_category, max_questions, " +
                "max_duration_minutes, status, total_question_count, created_at, updated_at, is_deleted) " +
                "VALUES (?, 'ONLINE', 'TECHNICAL', 'BACKEND', 10, 60, 'IN_PROGRESS', 0, NOW(), NOW(), FALSE) " +
                "RETURNING id",
            Long.class, u.getId()
        );
        jdbcTemplate.update(
            "INSERT INTO session_contexts (session_id, document_id, created_at) VALUES (?, ?, NOW())",
            sessionId, docId
        );

        assertThat(documentRepository.existsActiveSessionContextForResume(r.getId())).isTrue();
    }
}
