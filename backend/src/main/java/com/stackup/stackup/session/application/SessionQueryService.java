package com.stackup.stackup.session.application;

import com.stackup.stackup.session.application.dto.MessageResult;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.exception.SessionForbiddenException;
import com.stackup.stackup.session.application.exception.SessionNotFoundException;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionQueryService {

    private final InterviewSessionRepository sessionRepo;
    private final InterviewMessageRepository messageRepo;

    public SessionResult get(Long sessionId, Long userId) {
        InterviewSession s = sessionRepo.findByIdAndIsDeletedFalse(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!s.getUser().getId().equals(userId)) {
            throw new SessionForbiddenException(sessionId);
        }
        return SessionResult.from(s);
    }

    public Page<SessionResult> list(Long userId, Pageable pageable) {
        return sessionRepo
            .findByUser_IdAndIsDeletedFalseOrderByCreatedAtDesc(userId, pageable)
            .map(SessionResult::from);
    }

    public List<MessageResult> getMessages(Long sessionId, Long userId) {
        InterviewSession s = sessionRepo.findByIdAndIsDeletedFalse(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (!s.getUser().getId().equals(userId)) {
            throw new SessionForbiddenException(sessionId);
        }
        return messageRepo.findBySession_IdOrderBySequenceNumberAsc(sessionId)
            .stream().map(MessageResult::from).toList();
    }
}
