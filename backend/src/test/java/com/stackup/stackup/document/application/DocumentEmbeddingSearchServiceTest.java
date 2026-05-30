package com.stackup.stackup.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.dto.EmbeddingSearchCommand;
import com.stackup.stackup.document.application.dto.EmbeddingSearchResult;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.EmbeddingSearchRow;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocumentEmbeddingSearchServiceTest {

    private static final long SESSION_ID = 99L;
    private static final List<Long> DOCUMENT_IDS = List.of(42L, 17L);
    private static final float[] QUERY_EMBEDDING = embedding();

    @Mock
    private DocumentEmbeddingRepository embeddingRepository;

    @InjectMocks
    private DocumentEmbeddingSearchService service;

    @Test
    void search_rejectsEmptyDocumentIds() {
        EmbeddingSearchCommand command = new EmbeddingSearchCommand(
            SESSION_ID,
            List.of(),
            QUERY_EMBEDDING,
            8,
            null
        );

        assertThatThrownBy(() -> service.search(command))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.EMBEDDING_BAD_REQUEST)
            );
        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void search_rejectsEmptyQueryEmbedding() {
        EmbeddingSearchCommand command = new EmbeddingSearchCommand(
            SESSION_ID,
            DOCUMENT_IDS,
            new float[0],
            8,
            null
        );

        assertThatThrownBy(() -> service.search(command))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.EMBEDDING_BAD_REQUEST)
            );
        verifyNoInteractions(embeddingRepository);
    }

    @Test
    void search_usesDefaultTopKWhenTopKIsNull() {
        allowSearch(DOCUMENT_IDS);
        when(embeddingRepository.search(eq(SESSION_ID), eq(DOCUMENT_IDS), any(float[].class), anyInt(), isNull()))
            .thenReturn(List.of());
        EmbeddingSearchCommand command = new EmbeddingSearchCommand(
            SESSION_ID,
            DOCUMENT_IDS,
            QUERY_EMBEDDING,
            null,
            null
        );

        service.search(command);

        ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(embeddingRepository).search(
            eq(SESSION_ID),
            eq(DOCUMENT_IDS),
            eq(QUERY_EMBEDDING),
            topKCaptor.capture(),
            isNull()
        );
        assertThat(topKCaptor.getValue()).isEqualTo(8);
    }

    @Test
    void search_clampsOrRejectsTopKAboveMax() {
        allowSearch(DOCUMENT_IDS);
        when(embeddingRepository.search(eq(SESSION_ID), eq(DOCUMENT_IDS), any(float[].class), anyInt(), isNull()))
            .thenReturn(List.of());
        EmbeddingSearchCommand command = new EmbeddingSearchCommand(
            SESSION_ID,
            DOCUMENT_IDS,
            QUERY_EMBEDDING,
            21,
            null
        );

        try {
            service.search(command);

            ArgumentCaptor<Integer> topKCaptor = ArgumentCaptor.forClass(Integer.class);
            verify(embeddingRepository).search(
                eq(SESSION_ID),
                eq(DOCUMENT_IDS),
                eq(QUERY_EMBEDDING),
                topKCaptor.capture(),
                isNull()
            );
            assertThat(topKCaptor.getValue()).isLessThanOrEqualTo(20);
        } catch (DomainException exception) {
            assertThat(exception.getErrorCode()).isEqualTo(ApiErrorCode.EMBEDDING_BAD_REQUEST);
            verify(embeddingRepository, never())
                .search(anyLong(), anyList(), any(float[].class), anyInt(), any());
        }
    }

    @Test
    void search_returnsRepositoryRowsAsDto() {
        allowSearch(DOCUMENT_IDS);
        when(embeddingRepository.search(eq(SESSION_ID), eq(DOCUMENT_IDS), any(float[].class), eq(3), eq(0.2)))
            .thenReturn(List.of(
                new EmbeddingSearchRow(42L, 3, "Spring Security JWT filter", 0.86, "gemini-embedding-001"),
                new EmbeddingSearchRow(17L, 1, "Repository testcontainers setup", 0.74, "gemini-embedding-001")
            ));
        EmbeddingSearchCommand command = new EmbeddingSearchCommand(
            SESSION_ID,
            DOCUMENT_IDS,
            QUERY_EMBEDDING,
            3,
            0.2
        );

        List<EmbeddingSearchResult> results = service.search(command);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).documentId()).isEqualTo(42L);
        assertThat(results.get(0).chunkIndex()).isEqualTo(3);
        assertThat(results.get(0).chunkText()).isEqualTo("Spring Security JWT filter");
        assertThat(results.get(0).score()).isEqualTo(0.86);
        assertThat(results.get(0).model()).isEqualTo("gemini-embedding-001");
        assertThat(results.get(1).documentId()).isEqualTo(17L);
        assertThat(results.get(1).chunkIndex()).isEqualTo(1);
        verify(embeddingRepository).search(SESSION_ID, DOCUMENT_IDS, QUERY_EMBEDDING, 3, 0.2);
    }

    private void allowSearch(List<Long> documentIds) {
        lenient().when(embeddingRepository.existsActiveSession(SESSION_ID)).thenReturn(true);
        lenient().when(embeddingRepository.countSearchableSessionDocuments(SESSION_ID, documentIds))
            .thenReturn(documentIds.size());
    }

    private static float[] embedding() {
        float[] embedding = new float[1536];
        embedding[0] = 0.12f;
        embedding[1] = -0.03f;
        return embedding;
    }
}
