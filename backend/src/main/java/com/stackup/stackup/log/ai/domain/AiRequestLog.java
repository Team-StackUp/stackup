package com.stackup.stackup.log.ai.domain;

import com.stackup.stackup.common.entity.BaseTimeEntity;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
        name = "ai_request_logs",
        indexes = {
                @Index(name = "idx_ai_logs_user", columnList = "user_id, created_at"),
                @Index(name = "idx_ai_logs_session", columnList = "session_id"),
                @Index(name = "idx_ai_logs_type", columnList = "request_type, created_at"),
                @Index(name = "idx_ai_logs_created", columnList = "created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequestLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private InterviewSession session;

    @Column(name = "request_type", nullable = false, length = 50)
    private String requestType;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
