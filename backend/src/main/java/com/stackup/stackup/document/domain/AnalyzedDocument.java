package com.stackup.stackup.document.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import com.stackup.stackup.github.domain.GithubRepository;
import com.stackup.stackup.resume.domain.Resume;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "analyzed_documents",
        indexes = {
                @Index(name = "idx_analyzed_documents_resume_id", columnList = "resume_id"),
                @Index(name = "idx_analyzed_documents_repository_id", columnList = "repository_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalyzedDocument extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private GithubRepository repository;

    @Column(name = "document_path", length = 1000)
    private String documentPath;

    @Column(length = 2000)
    private String summary;

    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private String techStack;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.ACTIVE;

    @Column(name = "analysis_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AnalysisStatus analysisStatus = AnalysisStatus.PROCESSING;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "embedding_chunk_count", nullable = false)
    private int embeddingChunkCount = 0;

    private AnalyzedDocument(Resume resume, GithubRepository repository) {
        this.resume = resume;
        this.repository = repository;
    }

    public static AnalyzedDocument forResume(Resume resume) {
        if (resume == null) {
            throw new IllegalArgumentException("resume must not be null");
        }
        return new AnalyzedDocument(resume, null);
    }

    public static AnalyzedDocument forRepository(GithubRepository repository) {
        if (repository == null) {
            throw new IllegalArgumentException("repository must not be null");
        }
        return new AnalyzedDocument(null, repository);
    }

    public void markAnalyzed(String documentPath, String summary, String techStackJson, int embeddingChunkCount) {
        this.analysisStatus = AnalysisStatus.ANALYZED;
        this.documentPath = documentPath;
        this.summary = summary;
        this.techStack = techStackJson;
        this.embeddingChunkCount = embeddingChunkCount;
        this.errorCode = null;
        this.errorMessage = null;
    }

    public void markFailed(String errorCode, String errorMessage) {
        this.analysisStatus = AnalysisStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
