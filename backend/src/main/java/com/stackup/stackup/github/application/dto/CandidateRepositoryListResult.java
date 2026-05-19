package com.stackup.stackup.github.application.dto;

import java.util.List;

public record CandidateRepositoryListResult(
    List<CandidateRepositoryResult> content,
    int page,
    int perPage,
    boolean hasNext
) {
}
