package com.stackup.stackup.document.infrastructure;

import com.stackup.stackup.document.domain.DocumentEmbeddingRepository;
import java.sql.PreparedStatement;
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
