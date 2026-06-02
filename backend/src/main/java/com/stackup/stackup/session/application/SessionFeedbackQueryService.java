package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.FeedbackResult;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionFeedbackQueryService {

    private final InterviewSessionRepository sessionRepository;
    private final SessionFeedbackRepository feedbackRepository;

    public FeedbackResult get(Long userId, Long sessionId) {
        sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        SessionFeedback feedback = feedbackRepository.findBySession_Id(sessionId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.FEEDBACK_NOT_READY));
        return FeedbackResult.of(feedback);
    }

    // 공유 활성화: 소유자 검증 후 토큰 보장(없으면 발급). 멱등 — 현재 토큰 반환.
    @Transactional
    public String enableShare(Long userId, Long sessionId) {
        sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        SessionFeedback feedback = feedbackRepository.findBySession_Id(sessionId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.FEEDBACK_NOT_READY));
        return feedback.enableShare(UUID.randomUUID().toString());
    }

    // 공개 조회(비인증): 공유 토큰으로만. 없으면 404.
    public FeedbackResult getByToken(String shareToken) {
        SessionFeedback feedback = feedbackRepository.findByShareToken(shareToken)
            .orElseThrow(() -> new DomainException(ApiErrorCode.FEEDBACK_NOT_FOUND));
        return FeedbackResult.of(feedback);
    }
}
