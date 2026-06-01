package com.stackup.stackup.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.application.dto.AnalyzedDocumentResult;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentStatus;
import com.stackup.stackup.github.application.event.RepositoryDeletedEvent;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.resume.application.event.ResumeDeletedEvent;
import com.stackup.stackup.resume.domain.Resume;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyzedDocumentCascadeListenerTest {

    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock ObjectStorageClient storage;
    @InjectMocks AnalyzedDocumentCascadeListener listener;

    @Test
    void resumeDeleted_softDeletesRelatedAnalyzedDocuments() {
        AnalyzedDocument first = AnalyzedDocument.forResume(mock(Resume.class));
        AnalyzedDocument second = AnalyzedDocument.forResume(mock(Resume.class));
        when(documentRepository.findActiveByResumeIdAndOwner(10L, 1L)).thenReturn(List.of(first, second));

        listener.on(new ResumeDeletedEvent(1L, 10L));

        assertThat(first.isDeleted()).isTrue();
        assertThat(second.isDeleted()).isTrue();
    }

    @Test
    void repositoryDeleted_softDeletesRelatedAnalyzedDocuments() {
        AnalyzedDocument document = AnalyzedDocument.forRepository(mock(GithubRepository.class));
        when(documentRepository.findActiveByRepositoryIdAndOwner(20L, 1L)).thenReturn(List.of(document));

        listener.on(new RepositoryDeletedEvent(1L, 20L));

        assertThat(document.isDeleted()).isTrue();
    }

    @Test
    void listForUser_usesActiveDocumentQuerySoDeletedDocumentsAreExcluded() {
        AnalyzedDocumentQueryService queryService = new AnalyzedDocumentQueryService(documentRepository, storage);
        AnalyzedDocument active = mockDocument();
        when(documentRepository.findActiveByOwner(1L)).thenReturn(List.of(active));

        List<AnalyzedDocumentResult> results = queryService.listForUser(1L, null, null);

        assertThat(results).hasSize(1);
        verify(documentRepository).findActiveByOwner(1L);
        verify(documentRepository, never()).findByResume_User_IdOrRepository_User_Id(1L, 1L);
    }

    private AnalyzedDocument mockDocument() {
        AnalyzedDocument document = mock(AnalyzedDocument.class);
        when(document.getTechStack()).thenReturn("[\"Java\"]");
        when(document.getEmbeddingChunkCount()).thenReturn(1);
        when(document.getAnalysisStatus()).thenReturn(AnalysisStatus.ANALYZED);
        when(document.getStatus()).thenReturn(DocumentStatus.ACTIVE);
        return document;
    }
}
