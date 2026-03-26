package com.stackup.stackup.document.domain;

import com.stackup.stackup.common.entity.BaseSoftDeleteEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "analyzed_documents",
        indexes = {
                @Index(name = "idx_analyzed_docs_source", columnList = "source_type, source_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AnalyzedDocument extends BaseSoftDeleteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "document_path", nullable = false, length = 1000)
    private String documentPath;

    @Column(length = 2000)
    private String summary;

    @Column(name = "tech_stack", columnDefinition = "jsonb")
    private String techStack;

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
