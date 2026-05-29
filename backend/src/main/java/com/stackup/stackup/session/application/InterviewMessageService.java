package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.event.AnswerSubmittedEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
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
    public MessageResult submitAnswer(Long userId, Long sessionId, String content) {
        InterviewSession session = ownedSession(userId, sessionId);
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        InterviewMessage latest = messageRepository
            .findFirstBySession_IdOrderBySequenceNumberDesc(sessionId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_INVALID_STATE));
        if (latest.getRole() != com.stackup.stackup.session.domain.MessageRole.INTERVIEWER) {
            // 직전 메시지가 질문이 아니면 답변 불가
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        int nextSeq = latest.getSequenceNumber() + 1;
        InterviewMessage answer = messageRepository.save(
            InterviewMessage.interviewee(session, nextSeq, content, latest)
        );

        events.publishEvent(new AnswerSubmittedEvent(
            userId, sessionId, latest.getId(), answer.getId()
        ));
        return MessageResult.of(answer);
    }

    private InterviewSession ownedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
    }
}
