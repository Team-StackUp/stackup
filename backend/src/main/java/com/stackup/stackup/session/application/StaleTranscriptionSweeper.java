package com.stackup.stackup.session.application;

import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * STT 콜백을 영영 못 받은 음성 답변을 정리한다.
 *
 * <p>음성 답변은 `(transcribing)` placeholder 로 먼저 저장되고 `callback.voice` 가 도착해야
 * 채워진다. 그 콜백이 유실되면(AI 크래시·DLQ 격리·브로커 단절) 메시지는 그 상태로 남고,
 * 프론트의 턴 판정상 <b>답변 차례가 오지 않아 면접이 그대로 멈춘다</b> — 세션 시간 초과로
 * 통째로 끝날 때까지. 질문 생성 쪽은 실패 신호(callback status=FAILED)로 이미 해결했지만
 * 음성에는 대응이 없었다.
 *
 * <p>여기서는 오래 멈춘 placeholder 를 FAILED 로 확정한다. 그러면 기존 STT 실패 경로를 그대로
 * 타서(프론트 `currentTurn` 의 INTERVIEWEE+FAILED 분기, 백엔드 `resolveAnswerParent`)
 * 사용자는 같은 질문에 텍스트로 다시 답할 수 있다.
 */
@Component
@RequiredArgsConstructor
public class StaleTranscriptionSweeper {

    private static final Logger log = LoggerFactory.getLogger(StaleTranscriptionSweeper.class);

    private final InterviewMessageRepository messageRepository;
    private final VoiceTranscriptionRecoveryService recoveryService;

    // 이 시간이 지나도 전사가 안 채워지면 유실로 본다. 배치 STT 는 보통 수십 초 안에 끝난다.
    @Value("${interview.voice.stale-transcription-minutes:5}")
    private long staleAfterMinutes = 5;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @Scheduled(
        fixedDelayString = "${interview.voice.sweep-interval-ms:120000}",
        initialDelayString = "${interview.voice.sweep-initial-delay-ms:60000}")
    public void sweep() {
        Instant before = Instant.now().minus(Duration.ofMinutes(staleAfterMinutes));
        List<InterviewMessage> stale = messageRepository.findStaleTranscribing(
            InterviewMessage.VOICE_TRANSCRIPTION_PENDING_TEXT, before);
        if (stale.isEmpty()) {
            return;
        }
        int failed = 0;
        for (InterviewMessage m : stale) {
            try {
                // 메시지마다 독립 트랜잭션 — 하나가 실패해도 나머지는 정리된다.
                recoveryService.failStaleTranscription(m.getId());
                failed++;
            } catch (RuntimeException e) {
                log.warn("stale transcription recovery failed. messageId={}", m.getId(), e);
            }
        }
        log.info("stale transcription sweeper failed {} of {} pending voice answer(s)",
            failed, stale.size());
    }
}
