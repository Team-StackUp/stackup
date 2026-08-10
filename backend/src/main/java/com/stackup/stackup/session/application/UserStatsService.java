package com.stackup.stackup.session.application;

import com.stackup.stackup.session.application.dto.UserStatsResult;
import com.stackup.stackup.session.application.dto.UserStatsResult.AverageScores;
import com.stackup.stackup.session.application.dto.UserStatsResult.RecentScore;
import com.stackup.stackup.session.domain.InterviewSessionRepository;
import com.stackup.stackup.session.domain.SessionFeedback;
import com.stackup.stackup.session.domain.SessionFeedbackRepository;
import com.stackup.stackup.session.domain.SessionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// US-02: 사용자별 면접 통계 / 점수 추이.
// 데이터 없을 때 (신규 사용자) 모든 score 가 null / 0 으로 안전 반환.
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserStatsService {

    private static final int RECENT_LIMIT = 10;

    private final InterviewSessionRepository sessionRepository;
    private final SessionFeedbackRepository feedbackRepository;

    public UserStatsResult forUser(Long userId) {
        long total = sessionRepository.countByUser_IdAndDeletedFalse(userId);
        long completed = sessionRepository.countByUser_IdAndStatusAndDeletedFalse(userId, SessionStatus.COMPLETED);

        AverageScores avg = new AverageScores(
            feedbackRepository.averageOverallScore(userId),
            feedbackRepository.averageTechnicalAccuracy(userId),
            feedbackRepository.averageLogicScore(userId),
            feedbackRepository.averageCommunicationScore(userId)
        );

        List<RecentScore> recent = feedbackRepository
            .findRecentByOwner(userId, PageRequest.of(0, RECENT_LIMIT))
            .stream()
            .map(this::toRecent)
            .toList();

        return new UserStatsResult(total, completed, avg, recent);
    }

    private RecentScore toRecent(SessionFeedback f) {
        return new RecentScore(
            f.getSession().getId(),
            f.getOverallScore(),
            f.getTechnicalAccuracy(),
            f.getLogicScore(),
            f.getCommunicationScore(),
            f.getSession().getEndedAt()
        );
    }
}
