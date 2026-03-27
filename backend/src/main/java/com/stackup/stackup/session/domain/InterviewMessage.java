package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseTimeEntity;
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
        name = "interview_messages",
        indexes = {
                @Index(name = "idx_messages_session", columnList = "session_id, sequence_number"),
                @Index(name = "idx_messages_parent", columnList = "parent_message_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InterviewMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private InterviewSession session;

    @Column(name = "sequence_number", nullable = false)
    private Integer sequenceNumber;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "audio_file_path", length = 1000)
    private String audioFilePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private InterviewMessage parentMessage;

    @Column(nullable = false, length = 20)
    private String status = "CREATED";
}
