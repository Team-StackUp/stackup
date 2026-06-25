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
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
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
        try {
            session.start();
        } catch (IllegalStateException e) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult end(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        try {
            session.end();
        } catch (IllegalStateException e) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        events.publishEvent(new SessionEndedEvent(userId, sessionId, "USER_REQUEST"));
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult interrupt(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        try {
            session.interrupt();
        } catch (IllegalStateException e) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public SessionResult cancel(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        try {
            session.cancel();
        } catch (IllegalStateException e) {
            throw new DomainException(ApiErrorCode.SESSION_INVALID_STATE);
        }
        return SessionResult.of(session, contextDocumentIds(sessionId));
    }

    @Transactional
    public void delete(Long userId, Long sessionId) {
        InterviewSession session = loadOwned(userId, sessionId);
        sessionRepository.delete(session);
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
        return jobs + " " + command.mode().koreanLabel();
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
