package com.stackup.stackup.session.application;

import com.stackup.stackup.common.messaging.domain.ProcessedMessage;
import com.stackup.stackup.common.messaging.domain.ProcessedMessageRepository;
import com.stackup.stackup.common.messaging.RealtimeNotifyEvent;
import com.stackup.stackup.common.sse.SseEventType;
import com.stackup.stackup.session.application.dto.QuestionsCallbackEnvelope;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload;
import com.stackup.stackup.session.application.dto.QuestionsCallbackPayload.GeneratedQuestion;
import com.stackup.stackup.session.application.event.QuestionPersistedEvent;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import com.stackup.stackup.session.domain.MessageStatus;
import com.stackup.stackup.session.domain.SessionQuestionPool;
import com.stackup.stackup.session.domain.SessionQuestionPoolRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// callback.questions handling.
// POOL is kept as the legacy callback kind, but Core treats it as the single
// initial question result. Extra questions are ignored; Core does not manage a pool.
// FOLLOWUP inserts a follow-up question and pushes it through SSE.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionsCallbackService {

    private static final Logger log = LoggerFactory.getLogger(QuestionsCallbackService.class);
    private static final String CONSUMER_NAME = "core.callback.questions";

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final SessionQuestionPoolRepository poolRepository;
    private final ProcessedMessageRepository processedMessageRepository;
    private final ApplicationEventPublisher events;

    @Transactional
    public void apply(QuestionsCallbackEnvelope envelope) {
        if (envelope == null || envelope.payload() == null) {
            log.warn("callback.questions envelope or payload is null — skip");
            return;
        }
        QuestionsCallbackPayload payload = envelope.payload();
        if (isProcessed(envelope.messageId())) {
            log.info("callback.questions duplicate, skip. messageId={}", envelope.messageId());
            return;
        }
        Long sessionId = payload.sessionId();
        if (sessionId == null) {
            log.warn("callback.questions missing sessionId. messageId={}", envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted()) {
            log.warn("callback.questions session not found or deleted. id={}, messageId={}",
                sessionId, envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }
        // 종료된 세션에 늦게 도착한 콜백은 드롭(멱등 마킹). 스위퍼 자동종료·수동종료 후
        // 처리 대기 중이던 POOL/FOLLOWUP 콜백이 종료 세션을 사후 변조하는 것을 방지.
        if (session.getStatus().isTerminal()) {
            log.info("callback.questions for terminal session, drop. id={}, status={}, messageId={}",
                sessionId, session.getStatus(), envelope.messageId());
            markProcessed(envelope.messageId());
            return;
        }

        if (payload.isPool()) {
            if (payload.isFailed()) {
                applyPoolFailed(session, payload);
            } else {
                applyInitialQuestion(session, payload);
            }
        } else if (payload.isFollowup()) {
            if (payload.isFailed()) {
                applyFollowupFailed(session, payload);
            } else {
                applyFollowup(session, payload);
            }
        } else {
            log.warn("callback.questions unknown kind={}. messageId={}", payload.kind(), envelope.messageId());
        }
        markProcessed(envelope.messageId());
    }

    // 세션 생성 직후 호출: 모든 면접의 첫 질문인 자기소개 질문(seq=1)을 AI 없이 고정 삽입한다.
    // 이력서/레포 기반 질문 풀은 이 자기소개 답변을 받은 뒤에 생성된다(SessionQuestionsRequester).
    @Transactional
    public void insertSelfIntroduction(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.isDeleted()) {
            log.warn("self-intro insert skipped — session not found or deleted. id={}", sessionId);
            return;
        }
        // 멱등: 이미 메시지가 있으면(중복 이벤트) skip.
        if (messageRepository.countBySession_Id(sessionId) > 0) {
            log.info("self-intro insert skipped — messages already exist. sessionId={}", sessionId);
            return;
        }
        InterviewMessage message = messageRepository.save(InterviewMessage.selfIntroduction(session, 1));
        session.incrementQuestionCount();
        publishQuestionEvents(session, message, "SELF_INTRO_READY");
        log.info("self-intro question inserted. sessionId={}, msg={}", sessionId, message.getId());
    }

    // POOL 콜백: AI 가 만든 일반질문들을 풀에 저장하고 첫 질문을 삽입한다.
    private void applyInitialQuestion(InterviewSession session, QuestionsCallbackPayload payload) {
        List<GeneratedQuestion> questions = payload.questions();
        if (questions == null || questions.isEmpty()) {
            // status=OK 인데 결과가 비어 온 경우(예: 중복회피 필터링으로 전부 걸러짐).
            // 실패로 마킹되지 않았다는 이유로 조용히 무시하면 풀이 영원히 비어 있는 채
            // 세션만 IN_PROGRESS 로 남는다 — POOL 생성 실패와 동일하게 취급한다.
            log.warn("callback.questions initial result with no questions — treating as pool failure. "
                + "sessionId={}", session.getId());
            endSessionOnPoolFailure(session, "POOL_EMPTY_RESULT");
            return;
        }
        if (poolRepository.countBySessionId(session.getId()) > 0) {
            log.info("callback.questions pool already seeded, skip. sessionId={}", session.getId());
            return;
        }
        int idx = 0;
        String fallbackJobCategory = session.getJobCategory().name();
        for (GeneratedQuestion q : questions) {
            String jobCategory = (q.jobCategory() != null && !q.jobCategory().isBlank())
                ? q.jobCategory() : fallbackJobCategory;
            poolRepository.save(SessionQuestionPool.of(
                session.getId(), idx++, q.question(), q.category(), jobCategory,
                q.targetEvidence(), q.expectedSignal()));
        }
        // 요청한 개수(generalQuestionCount-1)보다 적게 왔으면 설정한 일반질문 수를 못 채우고
        // 조기 종료(POOL_EXHAUSTED)된다 — 사용자 눈엔 "마지막 질문 전에 갑자기 끝난" 것처럼
        // 보인다. 세션을 막지는 않되(부분 결과도 없는 것보다 낫다) 크게 로그를 남긴다.
        int expectedPoolCount = session.getGeneralQuestionCount() - 1;
        if (questions.size() < expectedPoolCount) {
            log.warn("callback.questions pool short of requested size — interview will end early. "
                    + "sessionId={}, requested={}, received={}",
                session.getId(), expectedPoolCount, questions.size());
        }
        poolRepository.findFirstBySessionIdAndUsedFalseOrderByIdxAsc(session.getId())
            .ifPresent(first -> insertGeneralFromPool(session, first, "INITIAL_QUESTION_READY"));
        log.info("callback.questions pool seeded. sessionId={}, poolSize={}", session.getId(), questions.size());
    }

    // POOL 생성 실패: 예전엔 SSE 로만 알리고 세션은 IN_PROGRESS 로 남겨뒀다(재시도는 프론트
    // 담당 가정). 그런데 실제로 재시도를 트리거하는 곳이 어디에도 없어서, 실패하면 세션이
    // "질문 준비 중"에 무기한 멈추고 최대 maxDurationMinutes(+스위퍼 주기)가 지나서야 타임아웃
    // 으로 종료됐다 — 사용자에게는 "면접이 갑자기 끝났다"로 보인다. 자기소개는 이미 답변된
    // 상태이므로(POOL 요청은 자기소개 답변 이후에만 발행됨) 여기서 바로 정상 종료시켜
    // 피드백 흐름을 태운다(꼬리질문 생성 실패를 advanceToNextGeneral 로 자연스럽게 이어가는
    // 것과 같은 원칙 — 조용히 멈추기보다 앞으로 나아가며 끝낸다).
    private void applyPoolFailed(InterviewSession session, QuestionsCallbackPayload payload) {
        log.warn("callback.questions POOL generation failed. sessionId={}, errorCode={}, retriable={}, message={}",
            session.getId(), payload.errorCode(), payload.retriable(), payload.errorMessage());
        endSessionOnPoolFailure(session, "POOL_GENERATION_FAILED");
    }

    private void endSessionOnPoolFailure(InterviewSession session, String reason) {
        if (session.getStatus() == SessionStatus.IN_PROGRESS) {
            endSession(session, reason);
        } else {
            // start() 이전(READY)이면 종료 전이가 불가능하다 — 실제 운영에서는 POOL 요청이
            // 자기소개 답변(=IN_PROGRESS) 이후에만 발행되므로 발생하지 않아야 하는 방어 분기.
            log.warn("callback.questions POOL failure on non-IN_PROGRESS session — cannot auto-end. "
                + "sessionId={}, status={}", session.getId(), session.getStatus());
        }
    }

    // 풀에서 다음 일반질문을 꺼내 삽입(꼬리질문 m개 소진 후 호출). 없으면 종료.
    @Transactional
    public void advanceToNextGeneral(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || session.getStatus() != SessionStatus.IN_PROGRESS) {
            return;
        }
        if (session.isMaxReached()) {
            endSession(session, "MAX_QUESTIONS_REACHED");
            return;
        }
        poolRepository.findFirstBySessionIdAndUsedFalseOrderByIdxAsc(sessionId).ifPresentOrElse(
            next -> insertGeneralFromPool(session, next, "GENERAL_QUESTION_READY"),
            () -> endSession(session, "POOL_EXHAUSTED"));
    }

    private void insertGeneralFromPool(InterviewSession session, SessionQuestionPool pool, String reason) {
        pool.markUsed();
        poolRepository.save(pool);
        // 시퀀스는 개수가 아니라 최대값 기준으로 매긴다. (session_id, sequence_number) 에
        // 유니크 제약이 있고 placeholder 를 지우는 경로(DONT_KNOW)가 있어서, 개수로 매기면
        // 삭제된 메시지가 마지막이 아닌 순간 이미 쓰인 번호를 다시 발급해 INSERT 가 터진다.
        int nextSeq = messageRepository.findMaxSequenceBySessionId(session.getId()) + 1;
        InterviewMessage message = messageRepository.save(
            InterviewMessage.interviewer(session, nextSeq, pool.getQuestion(),
                pool.getCategory(), pool.getTargetEvidence(), pool.getExpectedSignal()));
        session.incrementQuestionCount();
        publishQuestionEvents(session, message, reason);
        // 여기서 자동종료하지 않는다 — 방금 던진 메인질문은 답변·꼬리질문을 거쳐야 한다.
        // maxQuestions 도달 종료는 꼬리 사이클 후 advanceToNextGeneral 에서 판정한다.
    }

    private void publishQuestionEvents(InterviewSession session, InterviewMessage message, String reason) {
        events.publishEvent(new QuestionPersistedEvent(
            session.getUser().getId(), session.getId(), message.getId()));
        events.publishEvent(RealtimeNotifyEvent.session(session.getId(), SseEventType.SESSION_MESSAGE, message.getId()));
        events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.SESSION_MESSAGE,
            new SessionMessageNotice(session.getId(), message.getId(), reason)));
    }

    private void endSession(InterviewSession session, String reason) {
        // 원자적 종료 전이: IN_PROGRESS 일 때만 1행 갱신. 0이면 다른 트랜잭션(스위퍼·수동 종료)이
        // 먼저 종료한 것 → 종료 부수효과 미발행(중복 피드백 방지).
        int claimed = sessionRepository.finishIfInProgress(
            session.getId(), SessionStatus.COMPLETED, Instant.now());
        if (claimed == 0) {
            log.warn("auto-end skipped — session already ended. sessionId={}, status={}",
                session.getId(), session.getStatus());
            return;
        }
        session.end();  // 인메모리 동기화(SSE status 표기용). DB는 위 조건부 UPDATE 로 이미 COMPLETED.
        events.publishEvent(RealtimeNotifyEvent.session(session.getId(), SseEventType.SESSION_STATE,
            new SessionStateNotice(session.getId(), session.getStatus().name(), reason)));
        events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.SESSION_STATE,
            new SessionStateNotice(session.getId(), session.getStatus().name(), reason)));
        events.publishEvent(new SessionEndedEvent(session.getUser().getId(), session.getId(), reason));
        log.info("session auto-completed. sessionId={}, reason={}", session.getId(), reason);
    }

    private void applyFollowup(InterviewSession session, QuestionsCallbackPayload payload) {
        InterviewMessage parent = payload.parentMessageId() == null
            ? null
            : messageRepository.findById(payload.parentMessageId()).orElse(null);
        String intent = payload.answerIntent() == null ? "NORMAL" : payload.answerIntent();

        InterviewMessage placeholder = payload.followupMessageId() == null
            ? null
            : messageRepository.findById(payload.followupMessageId()).orElse(null);

        // 이미 실패로 확정된 placeholder 면 그 턴은 지나갔다 — 이어하기 복구
        // (SessionResumeService.recoverTurn)가 실패 처리하고 다음 일반질문으로 넘긴 뒤,
        // 늦게 도착한 콜백이 그 자리를 되살리면 살아있는 질문이 두 개가 된다.
        // 종료 세션은 terminal 가드가 막지만, 재개된 세션은 IN_PROGRESS 라 여기까지 온다.
        if (placeholder != null && placeholder.getStatus() == MessageStatus.FAILED) {
            log.info("callback.questions FOLLOWUP dropped — placeholder already failed (turn moved on). "
                + "sessionId={}, msg={}", session.getId(), placeholder.getId());
            return;
        }

        // 모르겠음 → 이 주제 그만, 다음 일반질문. placeholder 는 삭제(seq 연속성 유지).
        if ("DONT_KNOW".equalsIgnoreCase(intent)) {
            recordAnswerEvaluation(payload);
            if (placeholder != null) {
                messageRepository.delete(placeholder);
                messageRepository.flush();
            }
            log.info("callback.questions DONT_KNOW — advancing to next general. sessionId={}", session.getId());
            advanceToNextGeneral(session.getId());
            return;
        }

        if (payload.followupQuestion() == null || payload.followupQuestion().isBlank()) {
            log.warn("callback.questions FOLLOWUP empty question. sessionId={}", session.getId());
            return;
        }

        boolean clarification = "CLARIFICATION".equalsIgnoreCase(intent);
        if (!clarification) {
            recordAnswerEvaluation(payload);
        }

        InterviewMessage message;
        if (placeholder != null) {
            placeholder.completeFollowup(payload.followupQuestion(), clarification);
            message = messageRepository.save(placeholder);
        } else {
            // 레거시 폴백: placeholder 없이 도착한 콜백(롤아웃 호환).
            int nextSeq = messageRepository.findMaxSequenceBySessionId(session.getId()) + 1;
            message = messageRepository.save(clarification
                ? InterviewMessage.clarification(session, nextSeq, payload.followupQuestion(), parent)
                : InterviewMessage.followup(session, nextSeq, payload.followupQuestion(), parent));
        }

        // 꼬리질문은 maxQuestions(메인질문 수) 카운트에 포함하지 않고, 여기서 자동종료하지도
        // 않는다 — 종료는 메인질문의 꼬리 사이클이 끝나고 advanceToNextGeneral 시점에서만 일어나야
        // 답변 직후 생성된 꼬리질문을 버리고 갑자기 피드백으로 튕기는 일이 없다.
        publishQuestionEvents(session, message, clarification ? "CLARIFICATION" : "FOLLOWUP_READY");
        log.info("callback.questions FOLLOWUP processed. sessionId={}, msg={}, clarification={}",
            session.getId(), message.getId(), clarification);
    }

    // FOLLOWUP 생성 실패: placeholder 를 삭제하지 않고 실패 사실을 보여준 뒤(턴이 그냥
    // 사라진 것처럼 보이지 않게), DONT_KNOW 와 동일하게 다음 일반질문으로 진행해 면접이
    // 멈추지 않게 한다. retriable 여부는 로그·payload 로만 남기고(재시도 UI 는 후속 과제),
    // 이번 스코프는 "무기한 대기" 를 없애는 데 집중한다.
    private void applyFollowupFailed(InterviewSession session, QuestionsCallbackPayload payload) {
        log.warn("callback.questions FOLLOWUP generation failed. sessionId={}, errorCode={}, retriable={}, message={}",
            session.getId(), payload.errorCode(), payload.retriable(), payload.errorMessage());
        InterviewMessage placeholder = payload.followupMessageId() == null
            ? null
            : messageRepository.findById(payload.followupMessageId()).orElse(null);
        if (placeholder == null) {
            // 레거시 폴백(placeholder 없이 도착) — 갱신할 메시지가 없어 에러 이벤트로만 알린다.
            log.warn("callback.questions FOLLOWUP failed with no placeholder. sessionId={}", session.getId());
            publishErrorEvent(session, "FOLLOWUP", payload);
            return;
        }
        placeholder.failFollowup();
        InterviewMessage message = messageRepository.save(placeholder);
        publishQuestionEvents(session, message, "FOLLOWUP_FAILED");
        log.info("callback.questions FOLLOWUP marked failed, advancing to next general. sessionId={}, msg={}",
            session.getId(), message.getId());
        advanceToNextGeneral(session.getId());
    }

    // AI 가 보내는 errorMessage 는 `str(exc)` 그대로다 — LLM 게이트웨이 주소·조직 식별자·쿼터
    // 상세, 파싱 실패 시엔 모델 원문까지 들어올 수 있다. 이걸 SSE 로 흘리면 브라우저까지 그대로
    // 나간다(프론트는 쓰지도 않는다). 원문은 위 log.warn 이 서버에 남기고, 클라이언트에는
    // 우리가 정의한 코드로만 만든 안내 문구를 보낸다.
    private void publishErrorEvent(InterviewSession session, String scope, QuestionsCallbackPayload payload) {
        SessionErrorNotice notice = new SessionErrorNotice(
            session.getId(), scope, safeErrorCode(payload.errorCode()),
            userFacingMessage(payload.errorCode()), payload.retriable());
        events.publishEvent(RealtimeNotifyEvent.session(session.getId(), SseEventType.ERROR, notice));
        events.publishEvent(RealtimeNotifyEvent.user(session.getUser().getId(), SseEventType.ERROR, notice));
    }

    public record SessionStateNotice(Long sessionId, String status, String reason) {
    }

    private void recordAnswerEvaluation(QuestionsCallbackPayload payload) {
        QuestionsCallbackPayload.AnswerEvaluation eval = payload.answerEvaluation();
        if (eval == null || payload.answerMessageId() == null) {
            return;
        }
        messageRepository.findById(payload.answerMessageId())
            // 답변(INTERVIEWEE) 메시지에만 평가 기록 — 잘못된 messageId 로 질문에 점수가 달리는 것 방지.
            .filter(answer -> answer.getRole() == MessageRole.INTERVIEWEE)
            .ifPresent(answer -> {
                answer.recordAnswerEvaluation(
                    eval.specificity(), eval.logic(), eval.structure(), eval.correctness());
                messageRepository.save(answer);
            });
    }

    private boolean isProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return false;
        }
        return processedMessageRepository.existsById(messageId);
    }

    private void markProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            return;
        }
        try {
            processedMessageRepository.save(ProcessedMessage.of(messageId, CONSUMER_NAME));
        } catch (DataIntegrityViolationException ignored) {
            // race
        }
    }

    public record SessionMessageNotice(Long sessionId, Long messageId, String reason) {
    }

    // 알려진 코드만 통과시킨다. errorCode 도 결국 AI 가 채우는 문자열이라, 언젠가 예외 속성에서
    // 끌어다 쓰게 되면 같은 경로로 새어 나간다 — 화이트리스트로 값 범위를 묶어 둔다.
    private static final Map<String, String> ERROR_MESSAGES = Map.of(
        "GENERATION_FAILED", "질문 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.",
        "GENERATION_SCHEMA_INVALID", "질문 생성 결과를 해석하지 못했습니다. 잠시 후 다시 시도해 주세요."
    );
    private static final String UNKNOWN_ERROR_CODE = "GENERATION_FAILED";

    private static String safeErrorCode(String code) {
        return ERROR_MESSAGES.containsKey(code) ? code : UNKNOWN_ERROR_CODE;
    }

    private static String userFacingMessage(String code) {
        return ERROR_MESSAGES.getOrDefault(code, ERROR_MESSAGES.get(UNKNOWN_ERROR_CODE));
    }

    // errorMessage 가 아니라 message — 내부 원문이 아니라 사용자에게 보여줄 문구다.
    public record SessionErrorNotice(
        Long sessionId, String scope, String errorCode, String message, Boolean retriable
    ) {
    }
}
