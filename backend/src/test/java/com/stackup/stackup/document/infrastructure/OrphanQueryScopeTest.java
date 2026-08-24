package com.stackup.stackup.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * OrphanedObjectSweeper 의 대상 조회는 S3 객체를 실제로 지우는 데 쓰인다 —
 * 살아있는 행이 하나라도 섞이면 사용자의 이력서 원본이 사라진다.
 *
 * <p>파생 쿼리라 조건이 메서드 이름에 드러나지만, 이름을 고치다 조건이 빠지는 일은
 * 컴파일로 잡히지 않는다. 실제 DB 로 스코프를 고정한다.
 */
@PostgresRepositoryTest
class OrphanQueryScopeTest {

    @Autowired UserRepository userRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired AnalyzedDocumentRepository documentRepository;
    @Autowired EntityManager em;

    @Test
    void resumeOrphanQuerySelectsOnlyDeletedRowsWithRemainingFile() {
        User user = userRepository.save(User.createGithubUser(96001L, "orphan-user", null, null, "t"));
        Resume alive = resumeRepository.save(
            Resume.create(user, "alive.pdf", "resumes/raw/x/alive.pdf", ResumeFileType.PDF, 10L));
        Resume deleted = resumeRepository.save(
            Resume.create(user, "gone.pdf", "resumes/raw/x/gone.pdf", ResumeFileType.PDF, 10L));
        deleted.markDeleted();
        Resume alreadyPurged = resumeRepository.save(
            Resume.create(user, "done.pdf", "resumes/raw/x/done.pdf", ResumeFileType.PDF, 10L));
        alreadyPurged.markDeleted();
        alreadyPurged.markContentPurged();
        em.flush();

        assertThat(resumeRepository.findTop100ByDeletedTrueAndFilePathIsNotNull())
            .extracting(Resume::getId)
            .contains(deleted.getId())
            // 살아있는 자료는 절대 대상이 아니다.
            .doesNotContain(alive.getId())
            // 이미 회수된 건 다시 잡지 않는다.
            .doesNotContain(alreadyPurged.getId());
    }

    @Test
    void documentOrphanQuerySelectsOnlyDeletedRowsWithRemainingMarkdown() {
        User user = userRepository.save(User.createGithubUser(96002L, "orphan-doc-user", null, null, "t"));
        Resume resume = resumeRepository.save(
            Resume.create(user, "r.pdf", "resumes/raw/y/r.pdf", ResumeFileType.PDF, 10L));
        AnalyzedDocument alive = documentRepository.save(AnalyzedDocument.forResume(resume));
        alive.markAnalyzed("analyzed/alive.md", "s", "[]", 1);

        AnalyzedDocument deleted = documentRepository.save(AnalyzedDocument.forResume(resume));
        deleted.markAnalyzed("analyzed/gone.md", "s", "[]", 1);
        deleted.markDeleted();
        em.flush();

        assertThat(documentRepository.findTop100ByDeletedTrueAndDocumentPathIsNotNull())
            .extracting(AnalyzedDocument::getId)
            .contains(deleted.getId())
            .doesNotContain(alive.getId());
    }

    // 회수 완료 표시(file_path=NULL)가 DB 제약에 막히지 않아야 한다 — V32 가 삭제된 행을
    // 타입별 locator CHECK 에서 제외한다. 이게 막히면 스위퍼가 매 주기 같은 키를 다시 지운다.
    @Test
    void clearingFilePathIsAllowedForDeletedResume() {
        User user = userRepository.save(User.createGithubUser(96003L, "purge-mark-user", null, null, "t"));
        Resume resume = resumeRepository.save(
            Resume.create(user, "x.pdf", "resumes/raw/z/x.pdf", ResumeFileType.PDF, 10L));
        resume.markDeleted();
        resume.markContentPurged();

        em.flush();

        assertThat(resume.getFilePath()).isNull();
    }
}
