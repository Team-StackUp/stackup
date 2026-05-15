package com.stackup.stackup.document.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnalyzedDocumentRepository extends JpaRepository<AnalyzedDocument, Long> {

    List<AnalyzedDocument> findByResume_User_IdOrRepository_User_Id(Long resumeUserId, Long repositoryUserId);

    Optional<AnalyzedDocument> findByIdAndResume_User_IdOrIdAndRepository_User_Id(
            Long resumeDocumentId,
            Long resumeUserId,
            Long repositoryDocumentId,
            Long repositoryUserId
    );

    @Query(value = """
        SELECT EXISTS (
            SELECT 1
              FROM analyzed_documents d
              JOIN session_contexts sc ON sc.document_id = d.id
              JOIN interview_sessions s ON s.id = sc.session_id
             WHERE d.resume_id = :resumeId
               AND d.is_deleted = FALSE
               AND s.is_deleted = FALSE
               AND s.status IN ('READY', 'IN_PROGRESS')
        )
        """, nativeQuery = true)
    boolean existsActiveSessionContextForResume(@Param("resumeId") Long resumeId);
}
