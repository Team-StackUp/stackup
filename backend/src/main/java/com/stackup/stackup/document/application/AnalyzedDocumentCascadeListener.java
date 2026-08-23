package com.stackup.stackup.document.application;

import com.stackup.stackup.common.storage.ObjectPurgeEvent;
import com.stackup.stackup.coverletter.application.event.CoverLetterDeletedEvent;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.github.application.event.RepositoryDeletedEvent;
import com.stackup.stackup.resume.application.event.ResumeDeletedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Resume / GithubRepository / CoverLetter soft delete 발생 시 관련 AnalyzedDocument 도
// cascade soft delete + **분석 내용물 즉시 파기**(분석 마크다운 객체 + 임베딩 청크).
// 도메인 cycle 회피 — resume/github 가 document 도메인을 직접 import 하지 않고 이벤트로 위임.
@Component
@RequiredArgsConstructor
public class AnalyzedDocumentCascadeListener {

    private static final Logger log = LoggerFactory.getLogger(AnalyzedDocumentCascadeListener.class);

    private final AnalyzedDocumentRepository analyzedDocumentRepository;
    private final DocumentEmbeddingRepository embeddingRepository;
    private final ApplicationEventPublisher events;

    @EventListener
    @Transactional
    public void on(ResumeDeletedEvent event) {
        List<AnalyzedDocument> docs = analyzedDocumentRepository
            .findActiveByResumeIdAndOwner(event.resumeId(), event.userId());
        purge(docs);
        if (!docs.isEmpty()) {
            log.info("AnalyzedDocument cascade soft delete + purge (resume). userId={}, resumeId={}, count={}",
                event.userId(), event.resumeId(), docs.size());
        }
    }

    @EventListener
    @Transactional
    public void on(RepositoryDeletedEvent event) {
        List<AnalyzedDocument> docs = analyzedDocumentRepository
            .findActiveByRepositoryIdAndOwner(event.repositoryId(), event.userId());
        purge(docs);
        if (!docs.isEmpty()) {
            log.info("AnalyzedDocument cascade soft delete + purge (repository). userId={}, repositoryId={}, count={}",
                event.userId(), event.repositoryId(), docs.size());
        }
    }

    @EventListener
    @Transactional
    public void on(CoverLetterDeletedEvent event) {
        List<AnalyzedDocument> docs = analyzedDocumentRepository
            .findActiveByCoverLetterIdAndOwner(event.coverLetterId(), event.userId());
        purge(docs);
        if (!docs.isEmpty()) {
            log.info("AnalyzedDocument cascade soft delete + purge (cover letter). userId={}, coverLetterId={}, count={}",
                event.userId(), event.coverLetterId(), docs.size());
        }
    }

    /**
     * 문서를 soft delete 하고 **내용물은 즉시 파기**한다.
     *
     * <p>행을 지우지 않는 이유: session_contexts 가 analyzed_documents 를 FK 로 참조한다.
     * 하지만 남길 이유가 있는 건 참조 무결성뿐이고, 분석 마크다운(이력서를 재구성한 문서)과
     * 임베딩 청크(원문 조각)는 남길 이유가 없다.
     *
     * <p>임베딩은 같은 트랜잭션에서 지운다(DB). 스토리지 객체는 커밋 이후에 지운다 —
     * 롤백된 삭제로 객체를 날리면 되돌릴 수 없다.
     */
    private void purge(List<AnalyzedDocument> docs) {
        if (docs.isEmpty()) {
            return;
        }
        List<Long> ids = docs.stream().map(AnalyzedDocument::getId).toList();
        List<String> paths = docs.stream().map(AnalyzedDocument::getDocumentPath).toList();
        for (AnalyzedDocument doc : docs) {
            doc.markDeleted();
        }
        int removed = embeddingRepository.deleteByDocumentIds(ids);
        log.info("document embeddings purged. documentIds={}, chunks={}", ids, removed);
        events.publishEvent(new ObjectPurgeEvent(paths));
    }
}
