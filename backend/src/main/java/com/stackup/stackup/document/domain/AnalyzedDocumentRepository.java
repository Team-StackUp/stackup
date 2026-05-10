package com.stackup.stackup.document.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyzedDocumentRepository extends JpaRepository<AnalyzedDocument, Long> {

    List<AnalyzedDocument> findByResume_User_IdOrRepository_User_Id(Long resumeUserId, Long repositoryUserId);

    Optional<AnalyzedDocument> findByIdAndResume_User_IdOrIdAndRepository_User_Id(
            Long resumeDocumentId,
            Long resumeUserId,
            Long repositoryDocumentId,
            Long repositoryUserId
    );
}
