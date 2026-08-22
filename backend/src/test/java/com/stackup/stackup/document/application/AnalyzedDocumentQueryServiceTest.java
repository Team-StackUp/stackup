package com.stackup.stackup.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyzedDocumentQueryServiceTest {

    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock ObjectStorageClient storage;
    @InjectMocks AnalyzedDocumentQueryService service;

    // 분석 원문 프록시(A5) — presigned URL 은 내부 호스트라 브라우저 직접 접근 불가,
    // Core 가 소유권 검증 후 바이트를 중계한다.
    @Test
    void getContentForUser_streamsMarkdownFromStorage() {
        AnalyzedDocument doc = mock(AnalyzedDocument.class);
        when(doc.getDocumentPath()).thenReturn("analyzed/resume/42/summary.md");
        when(documentRepository.findActiveByIdAndOwner(42L, 1L)).thenReturn(Optional.of(doc));
        when(storage.get("analyzed/resume/42/summary.md"))
            .thenReturn(new ByteArrayInputStream("## 개요".getBytes()));

        assertThat(service.getContentForUser(1L, 42L)).isNotNull();
    }

    @Test
    void getContentForUser_throwsNotFoundForOthersDocument() {
        when(documentRepository.findActiveByIdAndOwner(42L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getContentForUser(1L, 42L))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.DOC_NOT_FOUND));
    }

    @Test
    void getContentForUser_throwsWhenNoDocumentPathYet() {
        AnalyzedDocument doc = mock(AnalyzedDocument.class);
        when(doc.getDocumentPath()).thenReturn(null);
        when(documentRepository.findActiveByIdAndOwner(42L, 1L)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.getContentForUser(1L, 42L))
            .isInstanceOfSatisfying(DomainException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(ApiErrorCode.DOC_NOT_ANALYZED));
    }
}
