package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 메시지 조회 + 사용자 답변 INSERT.
// 답변 commit 후 AnswerSubmittedEvent 발행 → 인프라가 generate.followup 발행.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterviewMessageService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    public List<MessageResult> list(Long userId, Long sessionId) {
        ownedSession(userId, sessionId);
        return messageRepository.findBySession_IdOrderBySequenceNumberAsc(sessionId).stream()
            .map(MessageResult::of)
            .toList();
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
