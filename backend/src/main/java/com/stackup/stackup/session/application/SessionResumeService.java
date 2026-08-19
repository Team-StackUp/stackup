package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SelfIntroAnsweredEvent;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중단된 면접 이어하기 (US-17 확장).
 *
 * <p>상태를 되돌리는 것만으로는 부족하다. 중단은 보통 <b>턴 한가운데</b>에서 일어나고,
 * 그동안 도착한 콜백은 terminal 가드가 전부 드롭했다. 그대로 재개하면 사용자는 답할 질문이
 * 없거나 "(생성 중)" 에 멈춰 있는 화면을 본다. 그래서 재개는 두 단계다:
 * <b>원자적 상태 전이 + 끊긴 턴 복구</b>.
 */
@Service
@RequiredArgsConstructor
public class SessionResumeService {

    private static final Logger log = LoggerFactory.getLogger(SessionResumeService.class);
    private static final String RESUME_REASON = "RESUMED";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final SessionContextRepository contextRepository;
    private final SessionQuestionPoolRepository poolRepository;
    private final QuestionsCallbackService questionsCallbackService;
    private final ApplicationEventPublisher events;

    @Transactional
    public SessionResult resume(Long userId, Long sessionId) {
        InterviewSession session = sessionRepository
            .findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));

        // 이어할 수 있는 건 중단된 세션뿐이다. 완료 세션은 피드백이 이미 나갔고,
        // 취소 세션은 시작한 적이 없다(둘 다 '다시 하기'로 새 세션을 만드는 게 맞다).
        if (session.getStatus() != SessionStatus.INTERRUPTED) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        // 원자적 재개 전이 — 중복 요청 중 하나만 차지한다(다른 전이와 같은 패턴).
        if (sessionRepository.resumeIfInterrupted(sessionId, Instant.now()) == 0) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        // 조건부 UPDATE 는 영속성 컨텍스트를 우회하므로 엔티티를 다시 읽는다.
        sessionRepository.flush();
        InterviewSession resumed = sessionRepository.findById(sessionId).orElseThrow();

        recoverTurn(userId, resumed);
        publishState(resumed);
        log.info("session resumed. sessionId={}, userId={}", sessionId, userId);
        return SessionResult.of(resumed, contextDocumentIds(sessionId));
    }

    /**
     * 끊긴 턴을 이어붙인다. 마지막 메시지가 무엇이냐로 갈린다.
     *
     * <ul>
     *   <li>정상 질문 → 할 일 없음. 사용자가 그 질문에 답하면 된다.
     *   <li>생성 중 placeholder → 그 꼬리질문은 영영 오지 않는다(콜백이 드롭됐다).
     *       실패로 확정하고 다음 일반질문으로 넘긴다.
     *   <li>자기소개 답변인데 질문 풀이 없음 → 풀 생성 요청이 유실된 것. 다시 요청한다.
     *   <li>그 외 답변 → 다음 질문이 오지 않은 것. 다음 일반질문으로 넘긴다.
     * </ul>
     */
    private void recoverTurn(Long userId, InterviewSession session) {
        InterviewMessage last = messageRepository
            .findFirstBySession_IdOrderBySequenceNumberDesc(session.getId())
            .orElse(null);
        if (last == null) {
            log.warn("resume: session has no messages — nothing to recover. sessionId={}",
                session.getId());
            return;
        }

        if (last.getRole() == MessageRole.INTERVIEWER) {
            if (!isPendingPlaceholder(last)) {
                return;  // 답할 질문이 그대로 있다
            }
            log.info("resume: dangling followup placeholder — failing and advancing. sessionId={}, msg={}",
                session.getId(), last.getId());
            last.failFollowup();
            questionsCallbackService.advanceToNextGeneral(session.getId());
            return;
        }

        // 마지막이 답변 = 다음 질문이 오지 않은 상태.
        InterviewMessage parent = last.getParentMessage();
        boolean selfIntroAnswer = parent != null && parent.isSelfIntroduction();
        if (selfIntroAnswer && poolRepository.countBySessionId(session.getId()) == 0) {
            log.info("resume: question pool never generated — re-requesting. sessionId={}",
                session.getId());
            requestQuestionPool(userId, session, last.getContent());
            return;
        }
        log.info("resume: answer without next question — advancing. sessionId={}", session.getId());
        questionsCallbackService.advanceToNextGeneral(session.getId());
    }

    // 내용이 아직 채워지지 않은 꼬리질문 placeholder 인지.
    private boolean isPendingPlaceholder(InterviewMessage message) {
        return InterviewMessage.FOLLOWUP_GENERATING_TEXT.equals(message.getContent());
    }

    // SessionFollowupRequester 가 자기소개 답변 직후 내는 것과 같은 이벤트.
    // AFTER_COMMIT 리스너(SessionQuestionsRequester)가 받아 generate.questions 를 발행한다.
    private void requestQuestionPool(Long userId, InterviewSession session, String selfIntroAnswer) {
        events.publishEvent(new SelfIntroAnsweredEvent(
            userId,
            session.getId(),
            session.getMode(),
            new ArrayList<>(session.getJobCategories()),
            session.getMaxQuestions(),
            session.getGeneralQuestionCount(),
            contextDocumentIds(session.getId()),
            selfIntroAnswer,
            session.getTargetCompanyName(),
            session.getTargetJobDescription()
        ));
    }

    private void publishState(InterviewSession session) {
        SessionTimeoutService.SessionStateNotice notice = new SessionTimeoutService.SessionStateNotice(
            session.getId(), SessionStatus.IN_PROGRESS.name(), RESUME_REASON);
        events.publishEvent(RealtimeNotifyEvent.session(
            session.getId(), SseEventType.SESSION_STATE, notice));
        events.publishEvent(RealtimeNotifyEvent.user(
            session.getUser().getId(), SseEventType.SESSION_STATE, notice));
    }

    private List<Long> contextDocumentIds(Long sessionId) {
        return contextRepository.findBySession_Id(sessionId).stream()
            .map(c -> c.getDocument().getId())
            .toList();
    }
}
