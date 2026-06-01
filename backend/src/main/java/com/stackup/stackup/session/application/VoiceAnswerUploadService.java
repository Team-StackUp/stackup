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

    @Transactional
    public MessageResult submit(Long userId, Long sessionId, VoiceAnswerUploadCommand cmd) {
        validate(cmd);
        InterviewSession session = sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));

        if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
            var existing = messageRepository.findBySession_IdAndIdempotencyKey(sessionId, cmd.idempotencyKey());
            if (existing.isPresent()) {
                return MessageResult.of(existing.get());
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

        // 메시지 ID 없이도 키를 만들어야 하므로 먼저 INSERT 후 key 갱신은 ID 의존. 단순화: 임시키로 PUT 후 INSERT.
        // 더 안전한 패턴: messageRepository.save 먼저 (id 채번) → key 결정 → S3 PUT → audio_file_path 갱신.
        InterviewMessage placeholder = messageRepository.save(
            InterviewMessage.voiceInterviewee(session, nextSeq, latest,
                cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank() ? cmd.idempotencyKey() : null)
        );
        String key = buildKey(sessionId, placeholder.getId(), cmd.contentType());
        storage.put(key, cmd.content(), cmd.size(), cmd.contentType());
        placeholder.attachAudio(key);

        AnalyzeVoicePayload payload = new AnalyzeVoicePayload(
            sessionId,
            placeholder.getId(),
            latest.getId(),
            key,
            cmd.contentType(),
            latest.getContent(),
            session.getMode().name(),
            session.getJobCategory().name()
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
        if (cmd.size() <= 0 || cmd.size() > MAX_BYTES) {
            throw new DomainException(ApiErrorCode.RESUME_FILE_TOO_LARGE);
        }
        if (cmd.contentType() == null || !ALLOWED_CONTENT_TYPES.contains(cmd.contentType().toLowerCase())) {
            throw new DomainException(ApiErrorCode.VOICE_INVALID_CONTENT_TYPE);
        }
    }

    private static String buildKey(Long sessionId, Long messageId, String contentType) {
        String ext = switch (contentType.toLowerCase()) {
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
