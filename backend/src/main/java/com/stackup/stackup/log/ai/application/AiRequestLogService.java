package com.stackup.stackup.log.ai.application;

import com.stackup.stackup.log.ai.application.dto.AiRequestLogCommand;
import com.stackup.stackup.log.ai.domain.AiRequestLog;
import com.stackup.stackup.log.ai.domain.AiRequestLogRepository;
import com.stackup.stackup.log.ai.domain.AiRequestStatus;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AiRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestLogService.class);

    private final AiRequestLogRepository logRepository;
    private final UserRepository userRepository;
    private final InterviewSessionRepository sessionRepository;

    public void record(AiRequestLogCommand cmd) {
        User user = cmd.userId() == null ? null : userRepository.findById(cmd.userId()).orElse(null);
        InterviewSession session = cmd.sessionId() == null
            ? null
            : sessionRepository.findById(cmd.sessionId()).orElse(null);
        AiRequestStatus status;
        try {
            status = AiRequestStatus.valueOf(cmd.status());
        } catch (IllegalArgumentException e) {
            status = AiRequestStatus.FAILED;
            log.warn("invalid ai_request_logs.status={}, fallback to FAILED", cmd.status());
        }
        logRepository.save(AiRequestLog.of(
            user, session,
            cmd.requestType(), cmd.modelName(),
            cmd.inputTokens(), cmd.outputTokens(), cmd.latencyMs(),
            status, cmd.errorMessage()
        ));
    }
}
