package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.session.domain.TtsStatus;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 메시지 조회 + 사용자 답변 INSERT.
// 답변 commit 후 AnswerSubmittedEvent 발행 → 인프라가 generate.followup 발행.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewMessageService {

    private static final Logger log = LoggerFactory.getLogger(InterviewMessageService.class);
    private static final Duration AUDIO_URL_TTL = Duration.ofMinutes(30);

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final ObjectStorageClient storage;
    private final ApplicationEventPublisher events;

    public List<MessageResult> list(Long userId, Long sessionId) {
        ownedSession(userId, sessionId);
        return messageRepository.findBySession_IdOrderBySequenceNumberAsc(sessionId).stream()
            .map(this::toResultWithAudioUrls)
            .toList();
    }

    // 재생용 presigned URL 동봉: 질문 TTS(SUCCEEDED) + 음성 답변 원본.
    // presign 실패가 메시지 조회 전체를 깨뜨리지 않도록 개별 try/catch.
    private MessageResult toResultWithAudioUrls(InterviewMessage m) {
        String ttsUrl = m.getTtsStatus() == TtsStatus.SUCCEEDED
            ? presign(m.getTtsAudioPath()) : null;
        String audioUrl = presign(m.getAudioFilePath());
        return MessageResult.of(m, ttsUrl, audioUrl);
    }

    private String presign(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            URI url = storage.createPresignedGetUrl(key, AUDIO_URL_TTL);
            return url == null ? null : url.toString();
        } catch (RuntimeException e) {
            log.warn("presigned audio URL creation failed. key={}", key, e);
            return null;
        }
    }

    // 오디오 스트리밍 프록시. MinIO presigned URL 이 내부 호스트라 브라우저가 못 가므로,
    // Core 가 인증을 거쳐 바이트를 그대로 흘려준다. 질문=TTS, 답변=음성 원본.
    public AudioStream streamAudio(Long userId, Long sessionId, Long messageId) {
        ownedSession(userId, sessionId);
        InterviewMessage m = messageRepository.findById(messageId)
            .filter(x -> x.getSession().getId().equals(sessionId))
            .orElseThrow(() -> new DomainException(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND));
        String key = m.getRole() == MessageRole.INTERVIEWER ? m.getTtsAudioPath() : m.getAudioFilePath();
        if (key == null || key.isBlank()) {
            throw new DomainException(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND);
        }
        return new AudioStream(storage.get(key), contentTypeForKey(key));
    }

    public record AudioStream(java.io.InputStream stream, String contentType) {}

    private static String contentTypeForKey(String key) {
        String k = key.toLowerCase();
        if (k.endsWith(".mp3")) return "audio/mpeg";
        if (k.endsWith(".wav")) return "audio/wav";
        if (k.endsWith(".ogg")) return "audio/ogg";
        if (k.endsWith(".m4a") || k.endsWith(".mp4")) return "audio/mp4";
        if (k.endsWith(".webm")) return "audio/webm";
        return "application/octet-stream";
    }

    @Transactional
    public MessageResult submitAnswer(Long userId, Long sessionId, String content, String idempotencyKey) {
        InterviewSession session = ownedSession(userId, sessionId);

        // SPRINT2_PLAN decision #4: SSE 재연결 자동 재시도 중복 차단
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = messageRepository.findBySession_IdAndIdempotencyKey(sessionId, idempotencyKey);
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
        InterviewMessage parentQuestion = resolveAnswerParent(latest);
        int nextSeq = latest.getSequenceNumber() + 1;
        InterviewMessage answer = messageRepository.save(
            InterviewMessage.interviewee(session, nextSeq, content, parentQuestion,
                idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null)
        );

        events.publishEvent(new AnswerSubmittedEvent(
            userId, sessionId, parentQuestion.getId(), answer.getId()
        ));
        return MessageResult.of(answer);
    }

    private InterviewMessage resolveAnswerParent(InterviewMessage latest) {
        if (latest.getRole() == MessageRole.INTERVIEWER) {
            return latest;
        }
        if (latest.getRole() == MessageRole.INTERVIEWEE
            && latest.getStatus() == MessageStatus.FAILED
            && latest.getAudioFilePath() != null
            && latest.getParentMessage() != null
            && latest.getParentMessage().getRole() == MessageRole.INTERVIEWER) {
            return latest.getParentMessage();
        }
        throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
    }

    private InterviewSession ownedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
    }
}
