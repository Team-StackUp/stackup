package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseTimeEntity;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "session_contexts",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_session_contexts_unique", columnNames = {"session_id", "document_id"})
        },
        indexes = {
                @Index(name = "idx_session_contexts_session", columnList = "session_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SessionContext extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private AnalyzedDocument document;
}
