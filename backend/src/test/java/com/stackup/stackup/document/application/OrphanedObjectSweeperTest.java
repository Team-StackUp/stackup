package com.stackup.stackup.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 스위퍼는 S3 객체를 실제로 지운다 — 대상 선정이 틀리면 살아있는 사용자의 이력서가 사라진다.
 * 그래서 "무엇을 지우는가"보다 "무엇을 건드리지 않는가"를 더 촘촘히 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class OrphanedObjectSweeperTest {

    @Mock ResumeRepository resumeRepository;
    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock ObjectStorageClient storage;
    @InjectMocks OrphanedObjectSweeper sweeper;

    @Test
    void purgesStorageObjectsOfDeletedMaterialsAndClearsPaths() {
        Resume resume = deletedResume("resumes/raw/1/a.pdf");
        AnalyzedDocument doc = deletedDocument("analyzed/resume/1/summary.md");
        when(resumeRepository.findTop100ByDeletedTrueAndFilePathIsNotNull()).thenReturn(List.of(resume));
        when(documentRepository.findTop100ByDeletedTrueAndDocumentPathIsNotNull()).thenReturn(List.of(doc));

        sweeper.sweep();

        verify(storage).delete("resumes/raw/1/a.pdf");
        verify(storage).delete("analyzed/resume/1/summary.md");
        // 경로를 비워 "회수 완료"를 표시한다 — 안 그러면 매 주기마다 같은 키를 다시 지우려 든다.
        assertThat(resume.getFilePath()).isNull();
        assertThat(doc.getDocumentPath()).isNull();
    }

    // 삭제 실패 시 경로를 남겨 다음 주기에 재시도한다. 지우지도 못했는데 완료 표시를 하면
    // 객체가 영구히 고아로 남는다.
    @Test
    void keepsPathWhenStorageDeleteFails() {
        Resume resume = deletedResume("resumes/raw/1/a.pdf");
        when(resumeRepository.findTop100ByDeletedTrueAndFilePathIsNotNull()).thenReturn(List.of(resume));
        when(documentRepository.findTop100ByDeletedTrueAndDocumentPathIsNotNull()).thenReturn(List.of());
        doThrow(new RuntimeException("storage down")).when(storage).delete(anyString());

        sweeper.sweep();

        assertThat(resume.getFilePath()).isEqualTo("resumes/raw/1/a.pdf");
    }

    // 한 건이 실패해도 나머지는 계속 회수해야 한다 — 예외를 전파하면 같은 배치가 통째로 막힌다.
    @Test
    void continuesAfterIndividualFailure() {
        Resume failing = deletedResume("resumes/raw/1/bad.pdf");
        AnalyzedDocument ok = deletedDocument("analyzed/resume/1/summary.md");
        when(resumeRepository.findTop100ByDeletedTrueAndFilePathIsNotNull()).thenReturn(List.of(failing));
        when(documentRepository.findTop100ByDeletedTrueAndDocumentPathIsNotNull()).thenReturn(List.of(ok));
        doThrow(new RuntimeException("boom")).when(storage).delete("resumes/raw/1/bad.pdf");

        sweeper.sweep();

        assertThat(failing.getFilePath()).isNotNull();
        assertThat(ok.getDocumentPath()).isNull();
    }

    @Test
    void doesNothingWhenNoOrphansRemain() {
        when(resumeRepository.findTop100ByDeletedTrueAndFilePathIsNotNull()).thenReturn(List.of());
        when(documentRepository.findTop100ByDeletedTrueAndDocumentPathIsNotNull()).thenReturn(List.of());

        sweeper.sweep();

        verify(storage, never()).delete(anyString());
    }

    private Resume deletedResume(String key) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        Resume resume = Resume.create(user, "a.pdf", key, ResumeFileType.PDF, 10L);
        resume.markDeleted();
        return resume;
    }

    private AnalyzedDocument deletedDocument(String path) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        Resume resume = Resume.create(user, "a.pdf", "resumes/raw/1/a.pdf", ResumeFileType.PDF, 10L);
        AnalyzedDocument doc = AnalyzedDocument.forResume(resume);
        ReflectionTestUtils.setField(doc, "documentPath", path);
        doc.markDeleted();
        return doc;
    }
}
