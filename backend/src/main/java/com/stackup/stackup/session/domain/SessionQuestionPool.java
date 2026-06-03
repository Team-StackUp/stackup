package com.stackup.stackup.session.domain;

import com.stackup.stackup.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

// AI 가 생성한 일반질문 풀 항목. 진행하며 idx 순으로 하나씩 꺼내 일반질문으로 삽입한다.
@Entity
@Table(name = "session_question_pool")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SessionQuestionPool extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Integer idx;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(length = 40)
    private String category;

    @Column(name = "target_evidence", columnDefinition = "text")
    private String targetEvidence;

    @Column(name = "expected_signal", columnDefinition = "text")
    private String expectedSignal;

    @Column(nullable = false)
    private boolean used = false;

    private SessionQuestionPool(Long sessionId, int idx, String question,
                                String category, String targetEvidence, String expectedSignal) {
        this.sessionId = sessionId;
        this.idx = idx;
        this.question = question;
        this.category = category;
        this.targetEvidence = targetEvidence;
        this.expectedSignal = expectedSignal;
    }

    public static SessionQuestionPool of(Long sessionId, int idx, String question,
                                         String category, String targetEvidence, String expectedSignal) {
        return new SessionQuestionPool(sessionId, idx, question, category, targetEvidence, expectedSignal);
    }

    public void markUsed() {
        this.used = true;
    }
}
