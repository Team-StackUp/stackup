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

    public static final String VOICE_TRANSCRIPTION_PENDING_TEXT = "(transcribing)";
    public static final String VOICE_TRANSCRIPTION_FAILED_TEXT =
        "음성 인식에 실패했습니다. 텍스트로 다시 답변해 주세요.";

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

    @Column(name = "tts_audio_path", length = 1000)
    private String ttsAudioPath;

    @Column(name = "tts_status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private TtsStatus ttsStatus = TtsStatus.NOT_REQUESTED;

    @Column(name = "tts_duration_sec")
    private Double ttsDurationSec;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_message_id")
    private InterviewMessage parentMessage;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private MessageStatus status = MessageStatus.CREATED;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    // 질문 메타데이터 (초기 질문에만 채워짐). category/targetEvidence 는 노출, expectedSignal 은 내부용.
    @Column(length = 30)
    private String category;

    @Column(name = "target_evidence", columnDefinition = "text")
    private String targetEvidence;

    @Column(name = "expected_signal", columnDefinition = "text")
    private String expectedSignal;

    // 답변 평가 (INTERVIEWEE 메시지에만 채워짐). 꼬리질문 단계 채점값 → 피드백 롤업에 재사용.
    @Column(name = "answer_specificity")
    private Double answerSpecificity;

    @Column(name = "answer_logic")
    private Double answerLogic;

    @Column(name = "answer_structure", length = 20)
    private String answerStructure;

    @Column(name = "answer_correctness")
    private Double answerCorrectness;

    // 부연(질문 재설명) 메시지 여부. true 면 총 질문 수·꼬리 깊이에 카운트하지 않는다.
    @Column(nullable = false)
    private boolean clarification = false;

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
        return interviewer(session, seq, content, null, null, null);
    }

    public static InterviewMessage interviewer(InterviewSession session, int seq, String content,
                                               String category, String targetEvidence,
                                               String expectedSignal) {
        InterviewMessage m = new InterviewMessage(session, seq, MessageRole.INTERVIEWER, content, null,
            MessageStatus.CREATED, null);
        m.category = category;
        m.targetEvidence = targetEvidence;
        m.expectedSignal = expectedSignal;
        m.markTtsPending();
        return m;
    }

    public static InterviewMessage followup(InterviewSession session, int seq, String content,
                                            InterviewMessage parent) {
        InterviewMessage m = new InterviewMessage(session, seq, MessageRole.INTERVIEWER, content, parent,
            MessageStatus.CREATED, null);
        m.markTtsPending();
        return m;
    }

    // 부연: 직전 질문을 다시 설명해 같은 차례를 유지. 질문 수/깊이에 카운트 안 함.
    public static InterviewMessage clarification(InterviewSession session, int seq, String content,
                                                 InterviewMessage parent) {
        InterviewMessage m = new InterviewMessage(session, seq, MessageRole.INTERVIEWER, content, parent,
            MessageStatus.CREATED, null);
        m.clarification = true;
        m.markTtsPending();
        return m;
    }

    public static InterviewMessage interviewee(InterviewSession session, int seq, String content,
                                               InterviewMessage parent, String idempotencyKey) {
        return new InterviewMessage(session, seq, MessageRole.INTERVIEWEE, content, parent,
            MessageStatus.COMPLETED, idempotencyKey);
    }

    // 음성 답변 placeholder. content 는 STT 완료 후 채움. 생성 시 CHECK 제약 만족을 위해 임시 공백 + audio_file_path 후속 갱신.
    public static InterviewMessage voiceInterviewee(InterviewSession session, int seq,
                                                    InterviewMessage parent, String idempotencyKey) {
        InterviewMessage m = new InterviewMessage(session, seq, MessageRole.INTERVIEWEE,
            VOICE_TRANSCRIPTION_PENDING_TEXT,
            parent, MessageStatus.CREATED, idempotencyKey);
        return m;
    }

    public void markStatus(MessageStatus newStatus) {
        if (newStatus != null) {
            this.status = newStatus;
        }
    }

    // 꼬리질문 콜백의 답변 평가를 이 답변 메시지에 기록 (피드백 재활용용).
    public void recordAnswerEvaluation(Double specificity, Double logic,
                                       String structure, Double correctness) {
        this.answerSpecificity = specificity;
        this.answerLogic = logic;
        this.answerStructure = structure;
        this.answerCorrectness = correctness;
    }

    public void attachAudio(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public void markTtsPending() {
        this.ttsStatus = TtsStatus.PENDING;
    }

    public void completeTts(String ttsAudioPath, Double ttsDurationSec) {
        if (ttsAudioPath != null && !ttsAudioPath.isBlank()) {
            this.ttsAudioPath = ttsAudioPath;
        }
        this.ttsDurationSec = ttsDurationSec;
        this.ttsStatus = TtsStatus.SUCCEEDED;
    }

    public void failTts() {
        this.ttsStatus = TtsStatus.FAILED;
    }

    public void completeWithTranscript(String transcript) {
        if (transcript != null && !transcript.isBlank()) {
            this.content = transcript;
        }
        this.status = MessageStatus.COMPLETED;
    }

    public void failVoiceTranscription() {
        this.content = VOICE_TRANSCRIPTION_FAILED_TEXT;
        this.status = MessageStatus.FAILED;
    }
}
