package com.stackup.stackup.document.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.document.application.dto.EmbeddingUpsertCommand;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.EmbeddingChunk;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentEmbeddingService {

    private final AnalyzedDocumentRepository documentRepository;
    private final DocumentEmbeddingRepository embeddingRepository;

    @Transactional
    public int upsert(EmbeddingUpsertCommand command) {
        if (!documentRepository.existsById(command.documentId())) {
            throw new DomainException(ApiErrorCode.DOC_NOT_FOUND);
        }
        List<EmbeddingChunk> mapped = command.chunks().stream()
            .map(c -> new EmbeddingChunk(c.chunkIndex(), c.chunkText(), c.embedding()))
            .toList();
        return embeddingRepository.upsertAll(command.documentId(), command.model(), mapped);
    }

    /**
     * 임베딩 검색 — **항상 요청자 소유 문서로 제한한다.**
     *
     * <p>이전에는 documentIds 가 비면 전체 사용자의 청크가 대상이었고 소유권 검증도 없었다.
     * 유출이 없었던 건 AI 호출부 3곳이 모두 빈 목록을 사전에 걸러줬기 때문인데, 방어가
     * 전적으로 호출자에게 있었다 — 호출부가 하나 늘거나 가드를 빠뜨리면 남의 이력서 청크가
     * 프롬프트로 들어간다. 여기서 스코프를 확정해 호출자와 무관하게 불가능하게 만든다.
     *
     * <p>documentIds 를 주면 그 중 소유한 것만 남기고(요청한 id 를 그대로 믿지 않는다),
     * 비어 있으면 소유 문서 전체가 대상이다. 교집합이 비면 검색하지 않고 빈 결과 —
     * 빈 목록을 그대로 넘기면 다시 전체 검색이 된다.
     */
    public List<DocumentEmbeddingRepository.SearchHit> search(
        Long userId, float[] queryEmbedding, String queryText, List<Long> documentIds, int topK
    ) {
        List<Long> owned = documentRepository.findActiveIdsByOwner(userId);
        List<Long> scoped = documentIds == null || documentIds.isEmpty()
            ? owned
            : documentIds.stream().filter(owned::contains).toList();
        if (scoped.isEmpty()) {
            return List.of();
        }
        return embeddingRepository.search(queryEmbedding, queryText, scoped, topK);
    }
}
