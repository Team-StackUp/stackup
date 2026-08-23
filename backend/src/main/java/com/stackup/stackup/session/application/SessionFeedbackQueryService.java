package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.storage.ObjectStorageClient;
import com.stackup.stackup.common.storage.StorageErrorType;
import com.stackup.stackup.common.storage.StorageException;
import com.stackup.stackup.session.application.event.FeedbackRegenerateRequestedEvent;
import com.stackup.stackup.session.application.dto.FeedbackResult;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionFeedbackQueryService {

    private final InterviewSessionRepository sessionRepository;
    private final SessionFeedbackRepository feedbackRepository;
    private final ApplicationEventPublisher events;
    private final ObjectStorageClient storage;

    public FeedbackResult get(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        SessionFeedback feedback = feedbackRepository.findBySession_Id(sessionId)
            .orElseThrow(() -> notReadyOrFailed(session));
        return FeedbackResult.of(feedback);
    }

    // 피드백 없음의 두 얼굴 구분: 실패 마커(V29)가 있으면 "생성 중"이 아니라 "실패"다 —
    // SSE ERROR 를 놓친(새로고침·재접속) 클라이언트도 폴링 즉시 복구 UI 로 전환할 수 있게.
    private DomainException notReadyOrFailed(InterviewSession session) {
        if (session.hasFeedbackFailure()) {
            Map<String, Object> details = session.getFeedbackFailRetriable() != null
                ? Map.of("retriable", session.getFeedbackFailRetriable())
                : Map.of();
            return new DomainException(ApiErrorCode.FEEDBACK_GENERATION_FAILED, details);
        }
        return new DomainException(ApiErrorCode.FEEDBACK_NOT_READY);
    }

    // AI 마크다운 리포트 프록시: presigned URL 은 내부(MinIO) 호스트라 브라우저가 직접
    // 접근할 수 없어 Core 가 바이트를 중계한다 (분석 원문 /content 프록시와 동일 패턴).
    public InputStream getReportContent(Long userId, Long sessionId) {
        SessionFeedback feedback = ownedFeedback(userId, sessionId);
        String path = feedback.getReportFilePath();
        if (path == null || path.isBlank()) {
            // 리포트는 부가 산출물 — AI 저장 실패 폴백(None)이나 구버전 피드백은 키가 없다.
            throw new DomainException(ApiErrorCode.FEEDBACK_REPORT_NOT_AVAILABLE);
        }
        try {
            return storage.get(path);
        } catch (StorageException e) {
            if (e.getType() == StorageErrorType.OBJECT_NOT_FOUND) {
                // 키만 남고 객체가 없는 정합 붕괴(볼륨 리셋 등) — 재시도해도 안 되는 503 대신
                // 키 부재와 같은 422 로: 클라이언트 처리 경로가 하나로 수렴한다.
                throw new DomainException(ApiErrorCode.FEEDBACK_REPORT_NOT_AVAILABLE);
            }
            throw e;
        }
    }

    // 공유 활성화: 소유자 검증 후 토큰 보장(없으면 발급). 멱등 — 현재 토큰 반환.
    @Transactional
    public String enableShare(Long userId, Long sessionId) {
        return ownedFeedback(userId, sessionId).enableShare(UUID.randomUUID().toString());
    }

    // 공유 해제: 토큰을 지워 기존 링크를 즉시 무효화. 멱등.
    @Transactional
    public void disableShare(Long userId, Long sessionId) {
        ownedFeedback(userId, sessionId).disableShare();
    }

    // 공개 조회(비인증): 공유 토큰으로만. 세션이 삭제됐으면 토큰이 남아 있어도 노출하지 않는다 —
    // 사용자가 '기록 삭제'로 기대하는 것은 공유 링크까지의 소멸이다.
    public FeedbackResult getByToken(String shareToken) {
        SessionFeedback feedback = feedbackRepository.findByShareToken(shareToken)
            .filter(f -> !f.isDeleted() && !f.getSession().isDeleted())
            .orElseThrow(() -> new DomainException(ApiErrorCode.FEEDBACK_NOT_FOUND));
        return FeedbackResult.of(feedback);
    }

    // 피드백 재생성 요청: generate.feedback 발행이 유실됐을 때(브로커 다운·AI 실패 DLQ)의
    // 복구 경로. COMPLETED 인데 피드백이 없는 세션만 허용 — 있으면 409 로 알려 재조회를 유도한다.
    @Transactional
    public void regenerate(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        if (session.getStatus() != SessionStatus.COMPLETED) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        if (feedbackRepository.existsBySession_Id(sessionId)) {
            throw new DomainException(ApiErrorCode.FEEDBACK_ALREADY_EXISTS);
        }
        // 재생성 요청 = 새 시도 시작 — 실패 마커를 지워 다른 탭/폴링도 "생성 중"으로 복귀시킨다.
        session.clearFeedbackFailure();
        // 발행은 commit 이후(AFTER_COMMIT 리스너) — 마커 clear 가 커밋되기 전에 새 시도의
        // 콜백이 도착하거나, 발행만 되고 커밋이 실패하는 역전을 막는다 (onSessionEnded 와 동일 규칙).
        events.publishEvent(new FeedbackRegenerateRequestedEvent(userId, sessionId));
    }

    private SessionFeedback ownedFeedback(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        // 실패 마커가 있으면 여기서도 "생성 중"이 아니라 "실패"로 응답 — GET 과 같은 계약.
        return feedbackRepository.findBySession_Id(sessionId)
            .orElseThrow(() -> notReadyOrFailed(session));
    }
}
