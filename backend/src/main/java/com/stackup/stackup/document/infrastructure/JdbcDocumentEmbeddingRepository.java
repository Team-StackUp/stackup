package com.stackup.stackup.document.infrastructure;

import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import com.stackup.stackup.document.domain.DocumentEmbeddingRepository.EmbeddingSearchRow;
import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
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

    private static final String EXISTS_ACTIVE_SESSION_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM interview_sessions
                WHERE id = ? AND is_deleted = FALSE
            )
            """;

    private static final String COUNT_SEARCHABLE_DOCUMENTS_SQL = """
            SELECT COUNT(DISTINCT ad.id)
            FROM session_contexts sc
            JOIN analyzed_documents ad ON ad.id = sc.document_id
            WHERE sc.session_id = ?
              AND ad.id = ANY(?::bigint[])
              AND ad.is_deleted = FALSE
              AND ad.analysis_status = 'ANALYZED'
            """;

    private final JdbcTemplate jdbc;

    public JdbcDocumentEmbeddingRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
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
    public boolean existsActiveSession(long sessionId) {
        Boolean exists = jdbc.queryForObject(EXISTS_ACTIVE_SESSION_SQL, Boolean.class, sessionId);
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public int countSearchableSessionDocuments(long sessionId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        Integer count = jdbc.query(
                COUNT_SEARCHABLE_DOCUMENTS_SQL,
                ps -> {
                    ps.setLong(1, sessionId);
                    ps.setArray(2, ps.getConnection().createArrayOf("bigint", documentIds.toArray(Long[]::new)));
                },
                rs -> rs.next() ? rs.getInt(1) : 0
        );
        return count == null ? 0 : count;
    }

    @Override
    public List<EmbeddingSearchRow> search(
            long sessionId,
            List<Long> documentIds,
            float[] queryEmbedding,
            int topK,
            Double minScore
    ) {
        if (documentIds == null || documentIds.isEmpty()) {
            return Collections.emptyList();
        }

        String queryVector = toVectorLiteral(queryEmbedding);
        String sql = """
                SELECT document_id, chunk_index, chunk_text, score, model
                FROM (
                    SELECT
                        de.document_id,
                        de.chunk_index,
                        de.chunk_text,
                        1 - (de.embedding <=> ?::vector) AS score,
                        de.model,
                        de.embedding <=> ?::vector AS distance
                    FROM document_embeddings de
                    JOIN session_contexts sc
                      ON sc.document_id = de.document_id
                     AND sc.session_id = ?
                    JOIN analyzed_documents ad
                      ON ad.id = de.document_id
                    WHERE ad.is_deleted = FALSE
                      AND ad.analysis_status = 'ANALYZED'
                      AND de.document_id = ANY(?::bigint[])
                ) ranked
                WHERE (? IS NULL OR score >= ?)
                ORDER BY distance
                LIMIT ?
                """;

        return jdbc.query(
                sql,
                ps -> {
                    ps.setString(1, queryVector);
                    ps.setString(2, queryVector);
                    ps.setLong(3, sessionId);
                    ps.setArray(4, ps.getConnection().createArrayOf("bigint", documentIds.toArray(Long[]::new)));
                    ps.setObject(5, minScore);
                    ps.setObject(6, minScore);
                    ps.setInt(7, topK);
                },
                (rs, rowNum) -> new EmbeddingSearchRow(
                        rs.getLong("document_id"),
                        rs.getInt("chunk_index"),
                        rs.getString("chunk_text"),
                        rs.getDouble("score"),
                        rs.getString("model")
                )
        );
    }

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
