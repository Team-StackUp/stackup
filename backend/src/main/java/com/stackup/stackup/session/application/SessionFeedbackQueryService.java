package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.FeedbackResult;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
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
}
