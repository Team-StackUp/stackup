package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.common.security.StreamTokenProvider;
import com.stackup.stackup.document.domain.AnalysisStatus;
import com.stackup.stackup.document.domain.AnalyzedDocument;
import com.stackup.stackup.document.domain.AnalyzedDocumentRepository;
import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.application.dto.SessionResult;
import com.stackup.stackup.session.application.event.SessionCreatedEvent;
import com.stackup.stackup.session.application.event.SessionEndedEvent;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import com.stackup.stackup.session.domain.SessionContext;
import com.stackup.stackup.session.domain.SessionContextRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// US-13~17: 면접 세션 생성/목록/단건/상태 전환/삭제.
// session_contexts 는 N:M (이력서/레포 분석 문서를 세션의 컨텍스트로 연결).
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionService {

    private final InterviewSessionRepository sessionRepository;
    private final SessionContextRepository contextRepository;
    private final AnalyzedDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final StreamTokenProvider streamTokenProvider;

    @Transactional
    public SessionResult create(Long userId, SessionCreateCommand command) {
        User user = loadUser(userId);
        String title = resolveTitle(command);
        // 직무 맞춤 모드는 채용공고(JD)가 질문·피드백의 핵심 근거이므로 필수.
        if (command.mode() == SessionMode.JOB_TAILORED
            && (command.targetJobDescription() == null || command.targetJobDescription().isBlank())) {
            throw new DomainException(ApiErrorCode.SESSION_JD_REQUIRED);
        }
        // 상한이 일반질문 수보다 작으면 AI 가 만든 질문 대부분이 버려진다 — 예: 일반질문 15,
        // 상한 2 면 풀 14개를 생성해 놓고 2번째 질문에서 MAX_QUESTIONS_REACHED 로 끝난다.
        // 프론트가 막고 있지만 API 를 직접 호출하면 조용히 낭비되므로 서버에서도 막는다.
        if (command.maxQuestions() != null && command.generalQuestionCount() != null
            && command.maxQuestions() < command.generalQuestionCount()) {
            throw new DomainException(ApiErrorCode.SESSION_QUESTION_COUNT_CONFLICT);
        }
        InterviewSession session = sessionRepository.save(InterviewSession.create(
            user,
            title,
            command.memo(),
            command.mode(),
            command.jobCategories(),
            command.maxQuestions(),
            command.maxDurationMinutes(),
            command.generalQuestionCount(),
            command.maxFollowupsPerQuestion()
        ));
        // 타깃 회사/JD 는 직무 맞춤 모드에서만 보관(다른 모드 입력값은 무시).
        if (command.mode() == SessionMode.JOB_TAILORED) {
            session.assignTargetRole(command.targetCompanyName(), command.targetJobDescription());
        }

        List<Long> linkedIds = linkContexts(session, userId, command.contextDocumentIds());

        // 세션 생성 commit 후 AI 질문 풀 생성 트리거 (US-18). 인프라스트럭처가 listener 에서 발행.
        events.publishEvent(new SessionCreatedEvent(
            userId,
            session.getId(),
            session.getMode(),
            command.jobCategories(),
            session.getMaxQuestions(),
            session.getGeneralQuestionCount(),
            linkedIds
        ));
        return SessionResult.of(session, linkedIds);
    }

    public Page<SessionResult> listPaged(Long userId, Pageable pageable) {
        loadUser(userId);
        return sessionRepository.findByUser_IdAndDeletedFalse(userId, pageable)
            .map(s -> SessionResult.of(s, contextDocumentIds(s.getId())));
    }

    public SessionResult get(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult start(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        // 원자적 시작 전이(end 와 동일 패턴): READY 일 때만 1행 갱신. 0이면 이미 시작/종료된 것.
        // 엔티티 검증(start)만으로는 두 트랜잭션이 같은 READY 스냅숏을 읽고 둘 다 통과할 수 있다.
        if (sessionRepository.startIfReady(sessionId, Instant.now()) == 0) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        session.start();  // 응답·인메모리 동기화(DB는 위 조건부 UPDATE 로 이미 IN_PROGRESS).
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult end(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        // 원자적 종료 전이: IN_PROGRESS 일 때만 1행 갱신. 0이면 다른 트랜잭션(스위퍼 등)이
        // 먼저 종료한 것 → 기존 동작과 동일하게 INVALID_STATE. 1을 받은 호출자만 종료 이벤트 발행.
        int claimed = sessionRepository.finishIfInProgress(
            sessionId, SessionStatus.COMPLETED, Instant.now());
        if (claimed == 0) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        session.end();  // 응답·인메모리 동기화(DB는 위 조건부 UPDATE 로 이미 COMPLETED).
        events.publishEvent(new SessionEndedEvent(userId, sessionId, "USER_REQUEST"));
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult interrupt(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        // IN_PROGRESS → INTERRUPTED 도 종료 전이 — 스위퍼·수동 end 와 경합하므로 같은 조건부
        // UPDATE 를 쓴다. INTERRUPTED 는 피드백을 만들지 않으므로 SessionEndedEvent 는 발행하지 않는다.
        if (sessionRepository.finishIfInProgress(sessionId, SessionStatus.INTERRUPTED, Instant.now()) == 0) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        session.interrupt();
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult cancel(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        if (sessionRepository.cancelIfReady(sessionId) == 0) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        session.cancel();
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        // 하드 DELETE 는 자식 FK(interview_messages·session_feedbacks·session_contexts 등,
        // 전부 ON DELETE 미지정) 위반으로 항상 500 이었다. soft delete 로 전환 —
        // 조회 경로(loadOwned/listPaged)는 이미 DeletedFalse 를 필터한다.
        session.markDeleted();
    }

    public String createSessionStreamToken(Long userId, Long sessionId) {
        sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
        return streamTokenProvider.createStreamToken(userId, "SESSION", sessionId);
    }

    @Transactional
    public SessionResult updateMeta(Long userId, Long sessionId, String title, String memo) {
        InterviewSession session = loadOwned(userId, sessionId);
        session.updateTitleAndMemo(title, memo);
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    // 제목 미입력 시 모드+직군으로 규칙 기반 제목 생성 (예: "프론트엔드·백엔드 기술 면접").
    // 히스토리에서 "면접 #id" 대신 의미 있는 제목을 보이게 한다.
    private String resolveTitle(SessionCreateCommand command) {
        String title = command.title();
        if (title != null && !title.isBlank()) {
            return title;
        }
        String jobs = command.jobCategories().stream()
            .map(JobCategory::koreanLabel)
            .collect(Collectors.joining("·"));
        String base = jobs + " " + command.mode().koreanLabel();
        // 직무 맞춤 면접은 회사명을 제목 앞에 붙여 히스토리·라이브 헤더에서 대상이 드러나게 한다.
        if (command.mode() == SessionMode.JOB_TAILORED
            && command.targetCompanyName() != null && !command.targetCompanyName().isBlank()) {
            return command.targetCompanyName().trim() + " " + base;
        }
        return base;
    }

    private User loadUser(Long userId) {
        return userRepository.findByIdAndDeletedFalse(userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.USER_NOT_FOUND));
    }

    private InterviewSession loadOwned(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));
    }

    private List<Long> linkContexts(InterviewSession session, Long userId, List<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }
        // 중복 제거 + 순서 보존
        LinkedHashSet<Long> unique = new LinkedHashSet<>(documentIds);
        List<Long> linked = new ArrayList<>(unique.size());
        for (Long documentId : unique) {
            AnalyzedDocument doc = documentRepository.findActiveByIdAndOwner(documentId, userId)
                .orElseThrow(() -> new DomainException(ApiErrorCode.DOC_NOT_FOUND));
            if (doc.getAnalysisStatus() != AnalysisStatus.ANALYZED) {
                throw new DomainException(ApiErrorCode.DOC_NOT_ANALYZED);
            }
            contextRepository.save(SessionContext.link(session, doc));
            linked.add(documentId);
        }
        return linked;
    }

    private List<Long> contextDocumentIds(Long sessionId) {
        return contextRepository.findBySession_Id(sessionId).stream()
            .map(c -> c.getDocument().getId())
            .toList();
    }
}
