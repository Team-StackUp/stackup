package com.stackup.stackup.common.messaging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "processed_messages",
    indexes = {
        @Index(name = "idx_processed_messages_processed_at", columnList = "processed_at")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedMessage {

    @Id
    @Column(name = "message_id", length = 64)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String consumer;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    private ProcessedMessage(String messageId, String consumer, Instant processedAt) {
        this.messageId = messageId;
        this.consumer = consumer;
        this.processedAt = processedAt;
    }

    public static ProcessedMessage of(String messageId, String consumer) {
        return new ProcessedMessage(messageId, consumer, Instant.now());
    }
}
