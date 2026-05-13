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

    @Column(name = "document_path", nullable = false, length = 1000)
    private String documentPath;

    @Column(length = 2000)
    private String summary;

    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private String techStack;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private DocumentStatus status = DocumentStatus.ACTIVE;
}
