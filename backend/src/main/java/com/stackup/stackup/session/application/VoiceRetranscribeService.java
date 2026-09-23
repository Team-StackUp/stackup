package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.VoiceAnswerUploadedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// STT 실패한 음성 답변을 같은 오디오로 다시 전사한다.
//
// 오디오는 S3 에 남아 있는데 꺼낼 경로가 없어서, 지금까지는 STT 가 한 번 실패하면 사용자가
// 답변을 통째로 다시 입력해야 했다. 실패의 대부분은 Deepgram 이 간헐적으로 멎는 것이고
// 같은 파일을 재전송하면 대체로 전사되므로(AI 서버의 자동 재시도가 1차 방어선), 그마저
// 소진됐을 때 사용자가 직접 당길 수 있는 2차 방어선을 둔다.
//
// 발행은 업로드 경로와 같은 VoiceAnswerUploadedEvent + AFTER_COMMIT(VoiceAnalysisRequester) —
// 이 트랜잭션이 롤백되면 AI 호출도 일어나지 않는다.
@Service
@RequiredArgsConstructor
public class VoiceRetranscribeService {

    private static final Logger log = LoggerFactory.getLogger(VoiceRetranscribeService.class);

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public MessageResult retranscribe(Long userId, Long sessionId, Long messageId) {
        InterviewSession session = sessionRepository
            .findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }

        InterviewMessage message = messageRepository.findById(messageId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND));
        if (!message.getSession().getId().equals(sessionId)) {
            throw new DomainException(ApiErrorCode.VOICE_MESSAGE_NOT_FOUND);
        }
        if (message.getRole() != MessageRole.INTERVIEWEE
            || message.getStatus() != MessageStatus.FAILED
            || message.getAudioFilePath() == null) {
            throw new DomainException(ApiErrorCode.VOICE_RETRANSCRIBE_NOT_ALLOWED);
        }

        // 실패 뒤 이미 다시 답변했다면 그 답변이 이 턴의 진짜 답이다. 여기서 전사를
        // 되살리면 뒤늦은 콜백이 지나간 턴의 내용을 덮어쓴다 — 마지막 메시지일 때만 허용.
        InterviewMessage latest = messageRepository
            .findFirstBySession_IdOrderBySequenceNumberDesc(sessionId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_INVALID_STATE));
        if (!latest.getId().equals(messageId)) {
            throw new DomainException(ApiErrorCode.VOICE_RETRANSCRIBE_NOT_ALLOWED);
        }

        message.retryVoiceTranscription();
        events.publishEvent(new VoiceAnswerUploadedEvent(
            userId, sessionId, messageId,
            message.getAudioFilePath(), contentTypeOf(message.getAudioFilePath())));
        log.info("voice retranscribe requested. sessionId={}, messageId={}, key={}",
            sessionId, messageId, message.getAudioFilePath());
        return MessageResult.of(message);
    }

    // content_type 은 저장하지 않으므로 업로드 때 확장자로 인코딩한 정보를 되읽는다
    // (VoiceAnswerSubmitService.buildKey 의 역함수). 알 수 없으면 Deepgram 이
    // 컨테이너를 스스로 판별하도록 octet-stream 으로 넘긴다.
    private static String contentTypeOf(String audioS3Key) {
        int dot = audioS3Key.lastIndexOf('.');
        String ext = dot < 0 ? "" : audioS3Key.substring(dot + 1).toLowerCase();
        return switch (ext) {
            case "webm" -> "audio/webm";
            case "ogg" -> "audio/ogg";
            case "mp3" -> "audio/mpeg";
            case "m4a" -> "audio/mp4";
            case "wav" -> "audio/wav";
            default -> "application/octet-stream";
        };
    }
}
