package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseTimeEntity;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "interview_messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_interview_messages_session_sequence", columnNames = {"session_id", "sequence_number"})
        },
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
    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Column(columnDefinition = "text")
    private String content;

    @Column(name = "audio_file_path", length = 1000)
    private String audioFilePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private InterviewMessage parentMessage;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.CREATED;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    private InterviewMessage(InterviewSession session, Integer sequenceNumber, MessageRole role,
                             String content, InterviewMessage parentMessage,
                             MessageStatus initialStatus, String idempotencyKey) {
        if (session == null) {
            throw new IllegalArgumentException("session must not be null");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be null or blank");
        }
        if (sequenceNumber == null || sequenceNumber < 1) {
            throw new IllegalArgumentException("sequence must be >= 1");
        }
        this.session = session;
        this.sequenceNumber = sequenceNumber;
        this.role = role;
        this.content = content;
        this.parentMessage = parentMessage;
        this.status = initialStatus;
        this.idempotencyKey = idempotencyKey;
    }

    public static InterviewMessage interviewer(InterviewSession session, int seq, String content) {
        return new InterviewMessage(session, seq, MessageRole.INTERVIEWER, content, null,
            MessageStatus.CREATED, null);
    }

    public static InterviewMessage followup(InterviewSession session, int seq, String content,
                                            InterviewMessage parent) {
        return new InterviewMessage(session, seq, MessageRole.INTERVIEWER, content, parent,
            MessageStatus.CREATED, null);
    }

    public static InterviewMessage interviewee(InterviewSession session, int seq, String content,
                                               InterviewMessage parent, String idempotencyKey) {
        return new InterviewMessage(session, seq, MessageRole.INTERVIEWEE, content, parent,
            MessageStatus.COMPLETED, idempotencyKey);
    }

    // 음성 답변 placeholder. content 는 STT 완료 후 채움. 생성 시 CHECK 제약 만족을 위해 임시 공백 + audio_file_path 후속 갱신.
    public static InterviewMessage voiceInterviewee(InterviewSession session, int seq,
                                                    InterviewMessage parent, String idempotencyKey) {
        InterviewMessage m = new InterviewMessage(session, seq, MessageRole.INTERVIEWEE, "(transcribing)",
            parent, MessageStatus.CREATED, idempotencyKey);
        return m;
    }

    public void markStatus(MessageStatus newStatus) {
        if (newStatus != null) {
            this.status = newStatus;
        }
    }

    public void attachAudio(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public void completeWithTranscript(String transcript) {
        if (transcript != null && !transcript.isBlank()) {
            this.content = transcript;
        }
        this.status = MessageStatus.COMPLETED;
    }
}
