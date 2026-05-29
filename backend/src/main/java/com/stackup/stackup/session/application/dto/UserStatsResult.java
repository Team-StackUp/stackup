package com.stackup.stackup.session.application.dto;

import java.util.List;

public record UserStatsResult(
    long totalSessionCount,
    long completedSessionCount,
    AverageScores averages,
    List<RecentScore> recent
) {
    public record AverageScores(
        Double overall,
        Double technical,
        Double logic,
        Double communication
    ) {
    }

    public record RecentScore(
        Long sessionId,
        Double overall,
        Double technical,
        Double logic,
        Double communication,
        java.time.Instant endedAt
    ) {
    }
}
