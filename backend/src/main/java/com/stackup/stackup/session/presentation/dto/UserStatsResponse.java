package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.UserStatsResult;
import java.time.Instant;
import java.util.List;

public record UserStatsResponse(
    long totalSessionCount,
    long completedSessionCount,
    AverageScores averages,
    List<RecentScore> recent
) {
    public record AverageScores(Double overall, Double technical, Double logic, Double communication) {
    }

    public record RecentScore(
        Long sessionId,
        Double overall,
        Double technical,
        Double logic,
        Double communication,
        Instant endedAt
    ) {
    }

    public static UserStatsResponse from(UserStatsResult r) {
        return new UserStatsResponse(
            r.totalSessionCount(),
            r.completedSessionCount(),
            new AverageScores(
                r.averages().overall(),
                r.averages().technical(),
                r.averages().logic(),
                r.averages().communication()
            ),
            r.recent().stream()
                .map(s -> new RecentScore(
                    s.sessionId(),
                    s.overall(),
                    s.technical(),
                    s.logic(),
                    s.communication(),
                    s.endedAt()
                ))
                .toList()
        );
    }
}
