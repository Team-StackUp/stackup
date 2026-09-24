package com.stackup.stackup.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.coverletter.domain.CoverLetter;
import com.stackup.stackup.document.application.AnalysisRequestService.AnalysisHandle;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.user.domain.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentReanalysisServiceTest {

    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock AnalysisRequestService analysisRequestService;
    @InjectMocks DocumentReanalysisService service;

    @Test
    void reanalyze_webResume_republishesWebAnalysis() {
        AnalyzedDocument doc = failedDocFor(webResume(18L, "https://portfolio.example.com/"));
        when(documentRepository.findActiveByIdAndOwner(34L, 1L)).thenReturn(Optional.of(doc));
        when(analysisRequestService.requestWebResumeAnalysis(1L, 18L))
            .thenReturn(new AnalysisHandle(99L, 18L, null));

        AnalysisHandle handle = service.reanalyze(1L, 34L);

        assertThat(handle.analyzedDocumentId()).isEqualTo(99L);
        // 실패 행을 남기면 목록에 실패 카드와 진행 카드가 나란히 보인다.
        assertThat(doc.isDeleted()).isTrue();
        verify(analysisRequestService, never()).requestResumeAnalysis(anyLong(), anyLong());
    }

    @Test
    void reanalyze_fileResume_republishesFileAnalysis() {
        AnalyzedDocument doc = failedDocFor(fileResume(5L));
        when(documentRepository.findActiveByIdAndOwner(20L, 1L)).thenReturn(Optional.of(doc));
        when(analysisRequestService.requestResumeAnalysis(1L, 5L))
            .thenReturn(new AnalysisHandle(77L, 5L, null));

        service.reanalyze(1L, 20L);

        verify(analysisRequestService).requestResumeAnalysis(1L, 5L);
        verify(analysisRequestService, never()).requestWebResumeAnalysis(anyLong(), anyLong());
    }

    @Test
    void reanalyze_repository_republishesRepositoryAnalysis() {
        GithubRepository repo = repository(7L);
        AnalyzedDocument doc = failed(AnalyzedDocument.forRepository(repo));
        when(documentRepository.findActiveByIdAndOwner(21L, 1L)).thenReturn(Optional.of(doc));
        when(analysisRequestService.requestRepositoryAnalysis(1L, 7L))
            .thenReturn(new AnalysisHandle(78L, null, 7L));

        service.reanalyze(1L, 21L);

        verify(analysisRequestService).requestRepositoryAnalysis(1L, 7L);
    }

    @Test
    void reanalyze_coverLetter_republishesCoverLetterAnalysis() {
        CoverLetter cl = CoverLetter.create(user(), "지원 동기", "[]");
        ReflectionTestUtils.setField(cl, "id", 3L);
        AnalyzedDocument doc = failed(AnalyzedDocument.forCoverLetter(cl));
        when(documentRepository.findActiveByIdAndOwner(22L, 1L)).thenReturn(Optional.of(doc));
        when(analysisRequestService.requestCoverLetterAnalysis(1L, 3L))
            .thenReturn(new AnalysisHandle(79L, null, null));

        service.reanalyze(1L, 22L);

        verify(analysisRequestService).requestCoverLetterAnalysis(1L, 3L);
    }

    @Test
    void reanalyze_rejectsDocumentThatDidNotFail() {
        AnalyzedDocument doc = AnalyzedDocument.forResume(fileResume(5L));
        doc.markAnalyzed("analyzed/resume/5.md", "요약", "[]", 3);
        when(documentRepository.findActiveByIdAndOwner(20L, 1L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.reanalyze(1L, 20L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.DOC_REANALYZE_NOT_ALLOWED);
        verifyNoInteractions(analysisRequestService);
    }

    @Test
    void reanalyze_rejectsDocumentNotOwnedByUser() {
        when(documentRepository.findActiveByIdAndOwner(20L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reanalyze(1L, 20L))
            .isInstanceOf(DomainException.class)
            .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.DOC_NOT_FOUND);
        verifyNoInteractions(analysisRequestService);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private AnalyzedDocument failedDocFor(Resume resume) {
        return failed(AnalyzedDocument.forResume(resume));
    }

    private AnalyzedDocument failed(AnalyzedDocument doc) {
        doc.markFailed("UNEXPECTED", "APITimeoutError: Request timed out.");
        assertThat(doc.getAnalysisStatus()).isEqualTo(AnalysisStatus.FAILED);
        return doc;
    }

    private Resume webResume(Long id, String url) {
        Resume r = Resume.createWeb(user(), "portfolio", url);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private Resume fileResume(Long id) {
        Resume r = Resume.create(
            user(), "resume.pdf", "resume/1/resume.pdf", ResumeFileType.PDF, 1024L);
        ReflectionTestUtils.setField(r, "id", id);
        return r;
    }

    private GithubRepository repository(Long id) {
        GithubRepository repo = GithubRepository.create(
            user(), 1234L, "app", "me/app", "https://github.com/me/app", "main");
        ReflectionTestUtils.setField(repo, "id", id);
        return repo;
    }

    private User user() {
        User u = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(u, "id", 1L);
        return u;
    }
}
