package com.stackup.stackup.document.infrastructure;

import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;


// 네이티브 쿼리가 더 쉬워서 JPA 대신 씀
@Repository
public class JdbcDocumentEmbeddingRepository implements DocumentEmbeddingRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO document_embeddings (document_id, chunk_index, chunk_text, embedding, model)
            VALUES (?, ?, ?, ?::vector, ?)
            ON CONFLICT (document_id, chunk_index)
            DO UPDATE SET
                chunk_text = EXCLUDED.chunk_text,
                embedding = EXCLUDED.embedding,
                model = EXCLUDED.model
            """;

    private static final String COUNT_SQL =
            "SELECT count(*) FROM document_embeddings WHERE document_id = ?";

    // 삭제된 문서의 청크는 검색에서 제외한다.
    //
    // 세션 생성 뒤 사용자가 워크스페이스에서 자료를 지워도 session_contexts 에는 그 문서 id 가
    // 남아 있고, generate.followup·generate.feedback 페이로드로 계속 실려 나간다
    // (SessionFollowupRequester/SessionFeedbackRequester 는 findBySession_Id 를 필터 없이 쓴다).
    // 여기서 걸러주지 않으면 지운 이력서 본문이 꼬리질문·채점 근거로 되살아난다 —
    // SessionQuestionsRequester.buildDocumentContexts 가 findActiveByIdAndOwner 로 막아둔 것과
    // 같은 문제가 RAG 경로에만 남아 있었다.
    //
    // 호출부마다 필터를 거는 대신 쿼리에서 막는 이유: 호출자가 늘 때마다 같은 실수를 반복할 수
    // 있고, 실제로 3개 호출부 중 어디도 삭제를 확인하지 않았다. 여기 한 곳이 마지막 관문이다.
    private static final String ACTIVE_DOC_JOIN =
        "JOIN analyzed_documents d ON d.id = e.document_id AND d.is_deleted = FALSE ";

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public JdbcDocumentEmbeddingRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.namedJdbc = new NamedParameterJdbcTemplate(dataSource);
    }

    @Override
    public int upsertAll(long documentId, String model, List<EmbeddingChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return 0;
        }
        int[][] perBatch = jdbc.batchUpdate(UPSERT_SQL, chunks, chunks.size(),
                (PreparedStatement ps, EmbeddingChunk c) -> {
                    ps.setLong(1, documentId);
                    ps.setInt(2, c.chunkIndex());
                    ps.setString(3, c.chunkText());
                    ps.setString(4, toVectorLiteral(c.embedding()));
                    ps.setString(5, model);
                });
        int total = 0;
        for (int[] batch : perBatch) {
            for (int n : batch) {
                total += Math.max(n, 0);
            }
        }
        return total == 0 ? chunks.size() : total;
    }

    @Override
    public int countByDocumentId(long documentId) {
        Integer n = jdbc.queryForObject(COUNT_SQL, Integer.class, documentId);
        return n == null ? 0 : n;
    }

    @Override
    public List<SearchHit> search(
        float[] queryEmbedding, String queryText, List<Long> documentIds, int topK) {
        if (queryEmbedding == null || queryEmbedding.length == 0) {
            return List.of();
        }
        int limit = topK <= 0 ? 5 : topK;
        boolean filterByDoc = documentIds != null && !documentIds.isEmpty();
        boolean hybrid = queryText != null && !queryText.isBlank();

        return hybrid
            ? searchHybrid(queryEmbedding, queryText, documentIds, filterByDoc, limit)
            : searchVectorOnly(queryEmbedding, documentIds, filterByDoc, limit);
    }

    private List<SearchHit> searchVectorOnly(
        float[] queryEmbedding, List<Long> documentIds, boolean filterByDoc, int limit) {
        StringBuilder sql = new StringBuilder(
            "SELECT e.document_id, e.chunk_index, e.chunk_text, "
            + "(e.embedding <=> CAST(:qvec AS vector)) AS distance "
            + "FROM document_embeddings e " + ACTIVE_DOC_JOIN);
        Map<String, Object> params = new HashMap<>();
        params.put("qvec", toVectorLiteral(queryEmbedding));
        if (filterByDoc) {
            sql.append("WHERE e.document_id IN (:documentIds) ");
            params.put("documentIds", documentIds);
        }
        sql.append("ORDER BY e.embedding <=> CAST(:qvec AS vector) LIMIT :limit");
        params.put("limit", limit);

        return namedJdbc.query(sql.toString(), params, ROW_MAPPER);
    }

    // 벡터(코사인) 랭킹과 full-text(ts_rank_cd) 랭킹을 각각 구한 뒤
    // RRF(Reciprocal Rank Fusion, k=60): score = 1/(k+rank) 합으로 융합한다.
    // 점수 스케일이 다른 두 랭킹을 "순위"만으로 합치므로 가중치 튜닝이 불필요.
    private List<SearchHit> searchHybrid(
        float[] queryEmbedding,
        String queryText,
        List<Long> documentIds,
        boolean filterByDoc,
        int limit) {
        String docFilterVec = filterByDoc ? "WHERE e.document_id IN (:documentIds) " : "";
        String docFilterFts = filterByDoc ? "AND e.document_id IN (:documentIds) " : "";

        // 주의: 이 SQL 은 **한 개의** 텍스트 블록이어야 한다. 중간에 문자열을 이어붙여 블록을
        // 쪼개면 `.formatted` 가 마지막 조각에만 걸려 placeholder 가 밀린다(실제로 그렇게 깨졌다).
        // 조각을 넣어야 하면 여기처럼 %%s 인자로 주입한다 — 순서는 등장 순.
        String sql = """
            WITH v AS (
                SELECT e.document_id, e.chunk_index, e.chunk_text,
                       (e.embedding <=> CAST(:qvec AS vector)) AS distance,
                       ROW_NUMBER() OVER (ORDER BY e.embedding <=> CAST(:qvec AS vector)) AS rnk
                FROM document_embeddings e
                %s
                %s
                ORDER BY e.embedding <=> CAST(:qvec AS vector)
                LIMIT :cand
            ),
            t AS (
                SELECT e.document_id, e.chunk_index, e.chunk_text,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank_cd(e.chunk_text_tsv, plainto_tsquery('simple', :qtext)) DESC
                       ) AS rnk
                FROM document_embeddings e
                %s
                WHERE e.chunk_text_tsv @@ plainto_tsquery('simple', :qtext)
                %s
                ORDER BY ts_rank_cd(e.chunk_text_tsv, plainto_tsquery('simple', :qtext)) DESC
                LIMIT :cand
            )
            SELECT COALESCE(v.document_id, t.document_id) AS document_id,
                   COALESCE(v.chunk_index, t.chunk_index) AS chunk_index,
                   COALESCE(v.chunk_text, t.chunk_text)   AS chunk_text,
                   COALESCE(v.distance, 1.0)              AS distance,
                   COALESCE(1.0 / (60 + v.rnk), 0) + COALESCE(1.0 / (60 + t.rnk), 0) AS rrf
            FROM v
            FULL OUTER JOIN t
              ON v.document_id = t.document_id AND v.chunk_index = t.chunk_index
            ORDER BY rrf DESC
            LIMIT :limit
            """.formatted(ACTIVE_DOC_JOIN, docFilterVec, ACTIVE_DOC_JOIN, docFilterFts);

        Map<String, Object> params = new HashMap<>();
        params.put("qvec", toVectorLiteral(queryEmbedding));
        params.put("qtext", queryText);
        params.put("cand", limit);
        params.put("limit", limit);
        if (filterByDoc) {
            params.put("documentIds", documentIds);
        }
        return namedJdbc.query(sql, params, ROW_MAPPER);
    }

    private static final org.springframework.jdbc.core.RowMapper<SearchHit> ROW_MAPPER =
        (rs, rowNum) -> new SearchHit(
            rs.getLong("document_id"),
            rs.getInt("chunk_index"),
            rs.getString("chunk_text"),
            rs.getDouble("distance")
        );

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder(embedding.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
