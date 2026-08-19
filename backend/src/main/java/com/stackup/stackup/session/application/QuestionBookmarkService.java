package com.stackup.stackup.session.application;

import com.stackup.stackup.common.exception.ApiErrorCode;
import com.stackup.stackup.common.exception.DomainException;
import com.stackup.stackup.session.application.dto.BookmarkedQuestionResult;
import com.stackup.stackup.session.domain.InterviewMessage;
import com.stackup.stackup.session.domain.InterviewMessageRepository;
import com.stackup.stackup.session.domain.InterviewSession;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.MessageRole;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 오답노트 — 다시 볼 질문 표시 + 모아보기.
// 표시는 질문(INTERVIEWER) 메시지에만 건다. 답변·부연 메시지를 표시해도 복습할 게 없다.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionBookmarkService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;

    @Transactional
    public boolean setBookmark(Long userId, Long sessionId, Long messageId, boolean bookmarked) {
        // 소유권은 세션으로 확인한다(메시지에는 user 가 없다).
        sessionRepository.findByIdAndUser_IdAndDeletedFalse(sessionId, userId)
            .orElseThrow(() -> new DomainException(ApiErrorCode.SESSION_NOT_FOUND));

        InterviewMessage message = messageRepository.findById(messageId)
            .filter(m -> m.getSession().getId().equals(sessionId))
            .orElseThrow(() -> new DomainException(ApiErrorCode.MESSAGE_NOT_FOUND));
        if (message.getRole() != MessageRole.INTERVIEWER) {
            throw new DomainException(ApiErrorCode.MESSAGE_NOT_BOOKMARKABLE);
        }
        message.applyBookmark(bookmarked);
        return bookmarked;
    }

    public List<BookmarkedQuestionResult> list(Long userId) {
        List<InterviewMessage> questions = messageRepository.findBookmarkedByOwner(userId);
        if (questions.isEmpty()) {
            return List.of();
        }
        // 질문마다 답변을 따로 조회하면 N+1 이 된다 — 한 번에 받아 매핑한다.
        Map<Long, InterviewMessage> answerByQuestionId = messageRepository
            .findByParentMessage_IdIn(questions.stream().map(InterviewMessage::getId).toList())
            .stream()
            .filter(m -> m.getRole() == MessageRole.INTERVIEWEE)
            .collect(Collectors.toMap(
                m -> m.getParentMessage().getId(), Function.identity(), (a, b) -> a));

        return questions.stream()
            .map(q -> toResult(q, answerByQuestionId.get(q.getId())))
            .toList();
    }

    private BookmarkedQuestionResult toResult(InterviewMessage question, InterviewMessage answer) {
        InterviewSession session = question.getSession();
        return new BookmarkedQuestionResult(
            question.getId(),
            session.getId(),
            session.getTitle(),
            question.getCategory(),
            question.getContent(),
            question.getExpectedSignal(),
            answer == null ? null : answer.getContent(),
            answer == null ? null : answer.getModelAnswer(),
            answer == null ? null : answer.getCoachingComment(),
            question.getCreatedAt()
        );
    }
}
