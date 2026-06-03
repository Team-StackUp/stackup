package com.stackup.stackup.session.application;

import com.stackup.stackup.common.config.properties.RabbitMqProperties;
import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.messaging.MessageContext;
import com.stackup.stackup.common.messaging.RabbitMessagePublisher;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.session.application.dto.AnalyzeVoicePayload;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.VoiceAnswerUploadCommand;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionStatus;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 음성 답변 업로드: S3 PUT → InterviewMessage INSERT (content=null, audio_file_path=key) → analyze.voice 발행.
// STT/분석 결과는 callback.voice 로 도착해서 InterviewMessage.content 채움 + voice metrics INSERT + followup 트리거.
@Service
@RequiredArgsConstructor
public class VoiceAnswerUploadService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnswerUploadService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav",
        "audio/x-wav", "audio/m4a", "audio/x-m4a"
    );
    private static final long MAX_BYTES = 25L * 1024 * 1024;  // Whisper API 25MB 제한

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final ObjectStorageClient storage;
    private final RabbitMessagePublisher publisher;
    private final RabbitMqProperties properties;

    // 스트리밍/배치 음성 답변이 공유하는 placeholder 생성 결과.
    public record VoicePlaceholder(InterviewSession session, InterviewMessage placeholder,
                                   InterviewMessage parentQuestion) {}

    // 세션 조회 → idempotency → 상태/직전메시지 검증 → placeholder save.
    // 배치 업로드(submit)와 스트리밍 시작(VoiceStreamService) 양쪽에서 재사용한다.
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
        InterviewMessage placeholder = messageRepository.save(
            InterviewMessage.voiceInterviewee(session, nextSeq, latest,
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
        );
        return new VoicePlaceholder(session, placeholder, latest);
    }

    @Transactional
    public MessageResult submit(Long userId, Long sessionId, VoiceAnswerUploadCommand cmd) {
        validate(cmd);
        VoicePlaceholder vp = createVoicePlaceholder(userId, sessionId, cmd.idempotencyKey());
        // idempotency 재호출이면 이미 완료된 메시지일 수 있음 — 기존 동작 유지: 그대로 반환.
        InterviewMessage placeholder = vp.placeholder();
        if (placeholder.getAudioFilePath() != null) {
            return MessageResult.of(placeholder);  // 이미 업로드됨(중복)
        }
        String key = buildKey(sessionId, placeholder.getId(), cmd.contentType());
        storage.put(key, cmd.content(), cmd.size(), cmd.contentType());
        placeholder.attachAudio(key);

        AnalyzeVoicePayload payload = new AnalyzeVoicePayload(
            sessionId,
            placeholder.getId(),
            vp.parentQuestion().getId(),
            key,
            cmd.contentType(),
            vp.parentQuestion().getContent(),
            vp.session().getMode().name(),
            vp.session().getJobCategory().name()
        );
        publisher.publishToAi(
            properties.routingKeys().analyzeVoice(),
            payload,
            new MessageContext(userId, sessionId, null, null)
        );
        log.info("analyze.voice published. sessionId={}, messageId={}, key={}",
            sessionId, placeholder.getId(), key);
        return MessageResult.of(placeholder);
    }

    private void validate(VoiceAnswerUploadCommand cmd) {
        if (cmd == null || cmd.content() == null || cmd.size() <= 0) {
            throw new DomainException(ApiErrorCode.VOICE_EMPTY_FILE);
        }
        if (cmd.size() > MAX_BYTES) {
            throw new DomainException(ApiErrorCode.VOICE_FILE_TOO_LARGE);
        }
        if (baseContentType(cmd.contentType()) == null) {
            throw new DomainException(ApiErrorCode.VOICE_INVALID_CONTENT_TYPE);
        }
    }

    // 브라우저 MediaRecorder 는 "audio/webm;codecs=opus" 처럼 코덱 파라미터를 붙인다.
    // 파라미터를 떼고 base MIME 만으로 허용 여부를 판단한다. 허용 외면 null.
    private static String baseContentType(String contentType) {
        if (contentType == null) {
            return null;
        }
        String base = contentType.split(";", 2)[0].trim().toLowerCase();
        return ALLOWED_CONTENT_TYPES.contains(base) ? base : null;
    }

    private static String buildKey(Long sessionId, Long messageId, String contentType) {
        String base = baseContentType(contentType);
        String ext = switch (base == null ? "" : base) {
            case "audio/webm" -> "webm";
            case "audio/ogg" -> "ogg";
            case "audio/mpeg" -> "mp3";
            case "audio/mp4", "audio/m4a", "audio/x-m4a" -> "m4a";
            case "audio/wav", "audio/x-wav" -> "wav";
            default -> "bin";
        };
        return "interview/voice/raw/%d/%d.%s".formatted(sessionId, messageId, ext);
    }
}
