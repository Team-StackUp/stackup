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
        LEFT JOIN d.resume rs
        LEFT JOIN rs.user ru
        LEFT JOIN d.repository rp
        LEFT JOIN rp.user pu
        LEFT JOIN d.coverLetter cl
        LEFT JOIN cl.user cu
        WHERE d.deleted = false
          AND (ru.id = :userId OR pu.id = :userId OR cu.id = :userId)
        ORDER BY d.id DESC
        """)
    List<AnalyzedDocument> findActiveByOwner(@Param("userId") Long userId);

    @Query("""
        SELECT d FROM AnalyzedDocument d
        LEFT JOIN d.resume rs
        LEFT JOIN rs.user ru
        LEFT JOIN d.repository rp
        LEFT JOIN rp.user pu
        LEFT JOIN d.coverLetter cl
        LEFT JOIN cl.user cu
        WHERE d.id = :id
          AND d.deleted = false
          AND (ru.id = :userId OR pu.id = :userId OR cu.id = :userId)
        """)
    Optional<AnalyzedDocument> findActiveByIdAndOwner(@Param("id") Long id, @Param("userId") Long userId);

    // 임베딩 검색 스코프 확정용 — 엔티티가 아니라 id 만 필요하다.
    @Query("""
        SELECT d.id FROM AnalyzedDocument d
        LEFT JOIN d.resume rs
        LEFT JOIN rs.user ru
        LEFT JOIN d.repository rp
        LEFT JOIN rp.user pu
        LEFT JOIN d.coverLetter cl
        LEFT JOIN cl.user cu
        WHERE d.deleted = false
          AND (ru.id = :userId OR pu.id = :userId OR cu.id = :userId)
        """)
    List<Long> findActiveIdsByOwner(@Param("userId") Long userId);

    // 분석 마크다운이 아직 남아 있는 **삭제된** 문서. OrphanedObjectSweeper 전용 —
    // 위와 같은 이유로 deleted=true 조건이 안전의 핵심이다.
    List<AnalyzedDocument> findTop100ByDeletedTrueAndDocumentPathIsNotNull();

    @Query("""
        SELECT d FROM AnalyzedDocument d
        WHERE d.coverLetter.id = :coverLetterId
          AND d.coverLetter.user.id = :userId
          AND d.deleted = false
        ORDER BY d.id DESC
        """)
    List<AnalyzedDocument> findActiveByCoverLetterIdAndOwner(
        @Param("coverLetterId") Long coverLetterId, @Param("userId") Long userId);

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
