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
            "SELECT document_id, chunk_index, chunk_text, (embedding <=> CAST(:qvec AS vector)) AS distance "
            + "FROM document_embeddings ");
        Map<String, Object> params = new HashMap<>();
        params.put("qvec", toVectorLiteral(queryEmbedding));
        if (filterByDoc) {
            sql.append("WHERE document_id IN (:documentIds) ");
            params.put("documentIds", documentIds);
        }
        sql.append("ORDER BY embedding <=> CAST(:qvec AS vector) LIMIT :limit");
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
        String docFilterVec = filterByDoc ? "WHERE document_id IN (:documentIds) " : "";
        String docFilterFts = filterByDoc ? "AND document_id IN (:documentIds) " : "";

        String sql = """
            WITH v AS (
                SELECT document_id, chunk_index, chunk_text,
                       (embedding <=> CAST(:qvec AS vector)) AS distance,
                       ROW_NUMBER() OVER (ORDER BY embedding <=> CAST(:qvec AS vector)) AS rnk
                FROM document_embeddings
                %s
                ORDER BY embedding <=> CAST(:qvec AS vector)
                LIMIT :cand
            ),
            t AS (
                SELECT document_id, chunk_index, chunk_text,
                       ROW_NUMBER() OVER (
                           ORDER BY ts_rank_cd(chunk_text_tsv, plainto_tsquery('simple', :qtext)) DESC
                       ) AS rnk
                FROM document_embeddings
                WHERE chunk_text_tsv @@ plainto_tsquery('simple', :qtext)
                %s
                ORDER BY ts_rank_cd(chunk_text_tsv, plainto_tsquery('simple', :qtext)) DESC
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
            """.formatted(docFilterVec, docFilterFts);

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
