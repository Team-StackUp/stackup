package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.VoiceAnswerUploadedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 음성 답변의 DB 쓰기 단계. S3 PUT · RabbitMQ 발행은 여기서 하지 않는다.
// - 업로드 오케스트레이션(검증 → S3 → 이 서비스 호출): VoiceAnswerSubmitService
// - analyze.voice 발행: VoiceAnalysisRequester (AFTER_COMMIT)
// 스트리밍 음성(stream-begin)은 createVoicePlaceholder 만 쓴다 — 오디오는 WS 로 흐른다.
@Service
@RequiredArgsConstructor
public class VoiceAnswerUploadService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnswerUploadService.class);

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    // 스트리밍/배치 음성 답변이 공유하는 placeholder 생성 결과.
    public record VoicePlaceholder(InterviewSession session, InterviewMessage placeholder,
                                   InterviewMessage parentQuestion) {}

    // 세션 조회 → idempotency → 상태/직전메시지 검증 → placeholder save.
    // 배치 업로드(VoiceAnswerSubmitService)와 스트리밍 시작(VoiceStreamService) 양쪽에서 재사용한다.
    @Transactional
    public VoicePlaceholder createVoicePlaceholder(Long userId, Long sessionId, String idempotencyKey) {
        InterviewSession session = sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = messageRepository.findBySession_IdAndIdempotencyKey(sessionId, idempotencyKey);
            if (existing.isPresent()) {
                InterviewMessage m = existing.get();
                return new VoicePlaceholder(session, m, m.getParentMessage());
            }
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        InterviewMessage latest = messageRepository
            .findFirstBySession_IdOrderBySequenceNumberDesc(sessionId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_INVALID_STATE));
        if (latest.getRole() != MessageRole.INTERVIEWER) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        int nextSeq = latest.getSequenceNumber() + 1;
        InterviewMessage placeholder;
        try {
            // 텍스트 답변(InterviewMessageService.submitAnswer)과 같은 이유로 saveAndFlush +
            // 제약 위반 변환 — 같은 턴에 동시 제출이 들어오면 둘 다 같은 seq 를 계산한다.
            placeholder = messageRepository.saveAndFlush(
                InterviewMessage.voiceInterviewee(session, nextSeq, latest,
                    idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
            );
        } catch (DataIntegrityViolationException e) {
            log.info("voice answer sequence conflict — turn already answered. sessionId={}, seq={}",
                sessionId, nextSeq);
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        return new VoicePlaceholder(session, placeholder, latest);
    }

    // 업로드된 오디오 키를 메시지에 붙이고 analyze.voice 요청 이벤트를 발행한다.
    // 발행은 AFTER_COMMIT 리스너가 받으므로, 이 트랜잭션이 롤백되면 AI 호출도 일어나지 않는다.
    @Transactional
    public MessageResult attachAudioAndRequestAnalysis(
        Long userId, Long sessionId, Long messageId, String audioS3Key, String contentType) {
        InterviewMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND));

        // 같은 Idempotency-Key 재요청이 경합해 이미 붙었으면 재발행하지 않는다.
        if (message.getAudioFilePath() != null) {
            return MessageResult.of(message);
        }
        message.attachAudio(audioS3Key);
        events.publishEvent(new VoiceAnswerUploadedEvent(
            userId, sessionId, message.getId(), audioS3Key, contentType));
        return MessageResult.of(message);
    }

    @Transactional(readOnly = true)
    public MessageResult describe(Long messageId) {
        return MessageResult.of(messageRepository.findById(messageId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND)));
    }

    // S3 업로드가 실패했을 때의 보상. placeholder 를 지우지 않고 FAILED 로 확정한다 —
    // 그냥 두면 STT 콜백이 영원히 오지 않아 "음성 인식 중…" 에서 턴이 잠긴다.
    // FAILED 로 두면 프론트가 턴을 풀고, 사용자는 같은 질문에 다시 답할 수 있다
    // (InterviewMessageService.resolveAnswerParent 의 FAILED 음성 답변 재답변 경로).
    @Transactional
    public void failVoiceUpload(Long sessionId, Long messageId) {
        InterviewMessage message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            return;
        }
        message.failVoiceTranscription();
        VoiceCallbackService.VoiceFailedNotice notice = new VoiceCallbackService.VoiceFailedNotice(
            sessionId, message.getId(), "VOICE_UPLOAD_FAILED", message.getContent());
        events.publishEvent(RealtimeNotifyEvent.session(sessionId, SseEventType.SESSION_MESSAGE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(message.getSession().getUser().getId(),
            SseEventType.SESSION_MESSAGE, notice));
        log.warn("voice upload failed — message marked FAILED. sessionId={}, messageId={}",
            sessionId, messageId);
    }
}
