package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.session.application.VoiceAnswerUploadService.VoicePlaceholder;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.VoiceAnswerUploadCommand;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

// 음성 답변 업로드 오케스트레이션: 검증 → placeholder INSERT(tx) → S3 PUT → 오디오 부착(tx).
//
// **트랜잭션 밖에서 S3 에 올린다.** 예전에는 이 메서드 전체가 @Transactional 이었고 그 안에서
// S3 PUT 과 RabbitMQ 발행을 했다. 두 가지가 문제였다:
//   1) 발행이 commit 보다 먼저 나가서, 이후 commit 이 실패하면 AI 는 존재하지 않는 메시지로
//      STT 를 돌리고 콜백은 "message not found" 로 드롭 — 사용자 답변이 조용히 증발했다.
//   2) 최대 25MB 업로드가 끝날 때까지 DB 커넥션을 붙잡아 커넥션 풀을 잠식했다.
// 발행은 VoiceAnalysisRequester 가 AFTER_COMMIT 에 하고, S3 는 트랜잭션 경계 밖으로 뺐다.
@Service
@RequiredArgsConstructor
public class VoiceAnswerSubmitService {

    private static final Logger log = LoggerFactory.getLogger(VoiceAnswerSubmitService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "audio/webm", "audio/ogg", "audio/mpeg", "audio/mp4", "audio/wav",
        "audio/x-wav", "audio/m4a", "audio/x-m4a"
    );
    private static final long MAX_BYTES = 25L * 1024 * 1024;  // Whisper API 25MB 제한

    private final VoiceAnswerUploadService uploadService;
    private final ObjectStorageClient storage;

    public MessageResult submit(Long userId, Long sessionId, VoiceAnswerUploadCommand cmd) {
        validate(cmd);
        VoicePlaceholder vp = uploadService.createVoicePlaceholder(userId, sessionId, cmd.idempotencyKey());
        Long messageId = vp.placeholder().getId();

        // idempotency 재호출이면 이미 업로드된 메시지 — 재업로드/재발행 없이 현재 상태를 반환.
        if (vp.placeholder().getAudioFilePath() != null) {
            return uploadService.describe(messageId);
        }

        String key = buildKey(sessionId, messageId, cmd.contentType());
        try {
            storage.put(key, cmd.content(), cmd.size(), cmd.contentType());
        } catch (RuntimeException e) {
            // placeholder 는 이미 commit 됐다. 그냥 두면 STT 콜백이 오지 않아 턴이 잠기므로
            // FAILED 로 확정해 사용자가 다시 답할 수 있게 한다.
            uploadService.failVoiceUpload(sessionId, messageId);
            log.warn("voice audio upload to storage failed. sessionId={}, messageId={}, key={}",
                sessionId, messageId, key, e);
            throw new DomainException(ApiErrorCode.VOICE_UPLOAD_FAILED);
        }
        return uploadService.attachAudioAndRequestAnalysis(
            userId, sessionId, messageId, key, cmd.contentType());
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
