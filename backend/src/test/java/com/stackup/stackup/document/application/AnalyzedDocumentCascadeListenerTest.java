package com.stackup.stackup.document.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stackup.stackup.common.storage.ObjectPurgeEvent;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.resume.application.event.ResumeDeletedEvent;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.user.domain.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 자료를 지우면 분석 내용물도 즉시 파기한다 — 분석 마크다운 객체 + 임베딩 청크.
 *
 * <p>행은 soft delete 로 남는다(session_contexts 가 FK 로 참조). 남길 이유가 있는 건
 * 참조 무결성뿐이고 내용물은 아니다.
 */
@ExtendWith(MockitoExtension.class)
class AnalyzedDocumentCascadeListenerTest {

    @Mock AnalyzedDocumentRepository documentRepository;
    @Mock DocumentEmbeddingRepository embeddingRepository;
    @Mock ApplicationEventPublisher events;
    @InjectMocks AnalyzedDocumentCascadeListener listener;

    @Test
    void resumeDeleted_softDeletesDocsAndPurgesContent() {
        AnalyzedDocument doc = analyzedDocument(11L, "analyzed/resume/11/summary.md");
        when(documentRepository.findActiveByResumeIdAndOwner(5L, 1L)).thenReturn(List.of(doc));

        listener.on(new ResumeDeletedEvent(1L, 5L));

        assertThat(doc.isDeleted()).isTrue();
        // 임베딩은 같은 트랜잭션에서 바로 지운다 — 청크에 이력서 본문이 그대로 들어 있다.
        verify(embeddingRepository).deleteByDocumentIds(List.of(11L));

        // 스토리지 객체는 커밋 이후 파기 — 이벤트로 넘긴다.
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue())
            .isInstanceOfSatisfying(ObjectPurgeEvent.class, e ->
                assertThat(e.keys()).containsExactly("analyzed/resume/11/summary.md"));
    }

    // 분석 전에 지운 자료는 documentPath 가 없다 — 빈 키로 파기 이벤트를 내지 않는다.
    @Test
    void resumeDeleted_skipsPurgeEventWhenNoDocumentPath() {
        AnalyzedDocument doc = analyzedDocument(12L, null);
        when(documentRepository.findActiveByResumeIdAndOwner(5L, 1L)).thenReturn(List.of(doc));

        listener.on(new ResumeDeletedEvent(1L, 5L));

        verify(embeddingRepository).deleteByDocumentIds(List.of(12L));
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(published.capture());
        assertThat(published.getValue())
            .isInstanceOfSatisfying(ObjectPurgeEvent.class, e -> assertThat(e.isEmpty()).isTrue());
    }

    @Test
    void resumeDeleted_isNoopWhenNoAnalyzedDocuments() {
        when(documentRepository.findActiveByResumeIdAndOwner(5L, 1L)).thenReturn(List.of());

        listener.on(new ResumeDeletedEvent(1L, 5L));

        verify(embeddingRepository, never()).deleteByDocumentIds(anyList());
        verify(events, never()).publishEvent(any());
    }

    private AnalyzedDocument analyzedDocument(Long id, String documentPath) {
        User user = User.createGithubUser(1L, "u", null, null, "t");
        ReflectionTestUtils.setField(user, "id", 1L);
        Resume resume = Resume.create(user, "r.pdf", "resumes/raw/1/r.pdf", ResumeFileType.PDF, 10L);
        AnalyzedDocument doc = AnalyzedDocument.forResume(resume);
        ReflectionTestUtils.setField(doc, "id", id);
        ReflectionTestUtils.setField(doc, "documentPath", documentPath);
        return doc;
    }
}
