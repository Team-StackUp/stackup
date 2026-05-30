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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "message_voice_analyses",
        indexes = {
                @Index(name = "idx_voice_analyses_message", columnList = "message_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageVoiceAnalysis extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false, unique = true)
    private InterviewMessage message;

    @Column(name = "speaking_rate_wpm")
    private Double speakingRateWpm;

    @Column(name = "silence_duration_sec")
    private Double silenceDurationSec;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filler_word_counts", columnDefinition = "jsonb")
    private String fillerWordCounts;

    @Column(name = "pronunciation_accuracy")
    private Double pronunciationAccuracy;

    private MessageVoiceAnalysis(InterviewMessage message, Double speakingRateWpm,
                                 Double silenceDurationSec, String fillerWordCountsJson,
                                 Double pronunciationAccuracy) {
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
        this.message = message;
        this.speakingRateWpm = speakingRateWpm;
        this.silenceDurationSec = silenceDurationSec;
        this.fillerWordCounts = fillerWordCountsJson;
        this.pronunciationAccuracy = pronunciationAccuracy;
    }

    public static MessageVoiceAnalysis of(InterviewMessage message, Double speakingRateWpm,
                                          Double silenceDurationSec, String fillerWordCountsJson,
                                          Double pronunciationAccuracy) {
        return new MessageVoiceAnalysis(message, speakingRateWpm, silenceDurationSec,
            fillerWordCountsJson, pronunciationAccuracy);
    }
}
