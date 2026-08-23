package com.stackup.stackup.document.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.EmbeddingChunk;
import com.stackup.stackup.document.application.DocumentEmbeddingService;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.SearchHit;
import com.stackup.stackup.resume.domain.Resume;
import com.stackup.stackup.resume.domain.ResumeFileType;
import com.stackup.stackup.resume.domain.ResumeRepository;
import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 임베딩 검색은 삭제된 문서의 청크를 돌려주면 안 된다.
 *
 * <p>세션 생성 뒤 사용자가 워크스페이스에서 이력서를 지워도, 세션 컨텍스트에는 그 문서 id 가
 * 그대로 남아 `generate.followup`·`generate.feedback` 페이로드로 계속 실려 나간다
 * (`SessionFollowupRequester`/`SessionFeedbackRequester` 는 `findBySession_Id` 를 필터 없이 쓴다).
 * 검색이 걸러주지 않으면 지운 이력서 본문이 꼬리질문·채점 근거로 되살아난다 —
 * `SessionQuestionsRequester.buildDocumentContexts` 가 `findActiveByIdAndOwner` 로 막아둔 것과
 * 같은 문제가 RAG 경로에만 남아 있었다.
 *
 * <p>호출자마다 필터를 거는 대신 검색 쿼리에서 막는다 — 호출자가 늘어날 때마다 같은 실수를
 * 반복할 수 있고, 실제로 3개 호출부 중 어디도 삭제를 확인하지 않았다.
 */
@PostgresRepositoryTest
@Import({JdbcDocumentEmbeddingRepository.class, DocumentEmbeddingService.class})
class DocumentEmbeddingSearchTest {

    @Autowired UserRepository userRepository;
    @Autowired ResumeRepository resumeRepository;
    @Autowired AnalyzedDocumentRepository documentRepository;
    @Autowired DocumentEmbeddingRepository embeddingRepository;
    @Autowired DocumentEmbeddingService embeddingService;
    @Autowired EntityManager em;

    @Test
    void searchExcludesChunksOfDeletedDocuments() {
        AnalyzedDocument kept = document(97001L, "kept");
        AnalyzedDocument removed = document(97002L, "removed");

        embeddingRepository.upsertAll(kept.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "살아있는 이력서 내용", vector(0.9f))));
        embeddingRepository.upsertAll(removed.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "지운 이력서 내용", vector(0.9f))));

        // 지우기 전에는 둘 다 잡힌다 — 필터가 "아무것도 안 거르는" 상태와 구분되게.
        assertThat(search(List.of(kept.getId(), removed.getId())))
            .extracting(SearchHit::documentId)
            .containsExactlyInAnyOrder(kept.getId(), removed.getId());

        removed.markDeleted();
        documentRepository.save(removed);
        em.flush();

        assertThat(search(List.of(kept.getId(), removed.getId())))
            .extracting(SearchHit::documentId)
            .containsExactly(kept.getId());
    }

    // documentIds 를 안 주면 전체 검색이다 — 이 경로에서도 삭제 문서가 새면 안 된다.
    @Test
    void unscopedSearchAlsoExcludesDeletedDocuments() {
        AnalyzedDocument removed = document(97003L, "removed-unscoped");
        embeddingRepository.upsertAll(removed.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "지운 문서 전체검색", vector(0.5f))));
        removed.markDeleted();
        documentRepository.save(removed);
        em.flush();

        assertThat(search(List.of()))
            .extracting(SearchHit::documentId)
            .doesNotContain(removed.getId());
    }

    // 하이브리드(queryText 동반) 경로도 같은 규약이어야 한다 — 벡터 CTE 만 막고 full-text CTE 를
    // 놓치면 본문 단어가 겹치는 순간 지운 문서가 그대로 올라온다.
    @Test
    void hybridSearchExcludesDeletedDocuments() {
        AnalyzedDocument removed = document(97004L, "removed-hybrid");
        embeddingRepository.upsertAll(removed.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "쿠버네티스 운영 경험", vector(0.5f))));
        removed.markDeleted();
        documentRepository.save(removed);
        em.flush();

        List<SearchHit> hits = embeddingRepository.search(
            vector(0.5f), "쿠버네티스", List.of(removed.getId()), 5);

        assertThat(hits).isEmpty();
    }

    // 검색 범위는 호출자가 뭘 보내든 요청자 소유 문서를 벗어나면 안 된다.
    // 이전에는 documentIds 를 그대로 믿었고, 비면 전체 사용자 청크가 대상이었다.
    @Test
    void serviceScopesSearchToRequestingUsersDocuments() {
        AnalyzedDocument mine = document(97005L, "mine");
        AnalyzedDocument stranger = document(97006L, "stranger");
        embeddingRepository.upsertAll(mine.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "내 이력서", vector(0.9f))));
        embeddingRepository.upsertAll(stranger.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "남의 이력서", vector(0.9f))));
        em.flush();

        Long myUserId = ownerOf(mine);

        // 남의 문서 id 를 명시해도 결과에 없어야 한다.
        assertThat(embeddingService.search(myUserId, vector(0.9f), null,
            List.of(mine.getId(), stranger.getId()), 10))
            .extracting(SearchHit::documentId)
            .containsExactly(mine.getId());

        // documentIds 를 비워도 전체 검색이 되지 않는다 — 소유 문서로만 좁혀진다.
        assertThat(embeddingService.search(myUserId, vector(0.9f), null, List.of(), 10))
            .extracting(SearchHit::documentId)
            .containsExactly(mine.getId());
    }

    // 소유 문서가 하나도 없으면 빈 목록을 그대로 넘기면 안 된다 — 넘기면 다시 전체 검색이다.
    @Test
    void serviceReturnsEmptyWhenUserOwnsNothing() {
        AnalyzedDocument stranger = document(97007L, "stranger-only");
        embeddingRepository.upsertAll(stranger.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "남의 문서뿐", vector(0.9f))));
        User loner = userRepository.save(User.createGithubUser(97008L, "loner", null, null, "t"));
        em.flush();

        assertThat(embeddingService.search(loner.getId(), vector(0.9f), null, List.of(), 10))
            .isEmpty();
    }

    // 자료를 지우면 청크 원문도 즉시 파기한다. 검색에서 빼는 것(#216)과는 다른 문제다 —
    // 행이 남아 있는 한 이력서 본문이 DB 에 그대로 있다.
    @Test
    void deleteByDocumentIdsRemovesChunks() {
        AnalyzedDocument kept = document(97009L, "kept-purge");
        AnalyzedDocument removed = document(97010L, "removed-purge");
        embeddingRepository.upsertAll(kept.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "남아야 하는 청크", vector(0.4f))));
        embeddingRepository.upsertAll(removed.getId(), "test-model",
            List.of(new EmbeddingChunk(0, "파기 대상", vector(0.4f)),
                new EmbeddingChunk(1, "파기 대상 2", vector(0.4f))));
        em.flush();

        assertThat(embeddingRepository.deleteByDocumentIds(List.of(removed.getId()))).isEqualTo(2);

        assertThat(embeddingRepository.countByDocumentId(removed.getId())).isZero();
        // 다른 문서의 청크까지 쓸어가면 안 된다.
        assertThat(embeddingRepository.countByDocumentId(kept.getId())).isEqualTo(1);
    }

    @Test
    void deleteByDocumentIdsIsNoopForEmptyInput() {
        assertThat(embeddingRepository.deleteByDocumentIds(List.of())).isZero();
    }

    private List<SearchHit> search(List<Long> documentIds) {
        return embeddingRepository.search(vector(0.9f), null, documentIds, 10);
    }

    private static float[] vector(float head) {
        float[] v = new float[1536];
        v[0] = head;
        return v;
    }

    private Long ownerOf(AnalyzedDocument doc) {
        return doc.getResume().getUser().getId();
    }

    private AnalyzedDocument document(Long githubId, String name) {
        User user = userRepository.save(User.createGithubUser(githubId, name, null, null, "t"));
        Resume resume = resumeRepository.save(
            Resume.create(user, name + ".pdf", "resumes/raw/x/" + name + ".pdf", ResumeFileType.PDF, 10L));
        AnalyzedDocument doc = documentRepository.save(AnalyzedDocument.forResume(resume));
        em.flush();
        return doc;
    }
}
