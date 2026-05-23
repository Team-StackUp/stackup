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

    @Query("""
        SELECT d FROM AnalyzedDocument d
        WHERE d.deleted = false
          AND ((d.resume IS NOT NULL AND d.resume.user.id = :userId)
            OR (d.repository IS NOT NULL AND d.repository.user.id = :userId))
        ORDER BY d.id DESC
        """)
    List<AnalyzedDocument> findActiveByOwner(@Param("userId") Long userId);

    @Query("""
        SELECT d FROM AnalyzedDocument d
        WHERE d.id = :id
          AND d.deleted = false
          AND ((d.resume IS NOT NULL AND d.resume.user.id = :userId)
            OR (d.repository IS NOT NULL AND d.repository.user.id = :userId))
        """)
    Optional<AnalyzedDocument> findActiveByIdAndOwner(@Param("id") Long id, @Param("userId") Long userId);

    @Query("""
        SELECT d FROM AnalyzedDocument d
        WHERE d.resume.id = :resumeId
          AND d.resume.user.id = :userId
          AND d.deleted = false
        ORDER BY d.id DESC
        """)
    List<AnalyzedDocument> findActiveByResumeIdAndOwner(@Param("resumeId") Long resumeId, @Param("userId") Long userId);

    @Query("""
        SELECT d FROM AnalyzedDocument d
        WHERE d.repository.id = :repositoryId
          AND d.repository.user.id = :userId
          AND d.deleted = false
        ORDER BY d.id DESC
        """)
    List<AnalyzedDocument> findActiveByRepositoryIdAndOwner(@Param("repositoryId") Long repositoryId, @Param("userId") Long userId);
}
