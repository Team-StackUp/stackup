package com.stackup.stackup.github.presentation.dto;

import com.stackup.stackup.github.application.dto.CandidateRepositoryListResult;
import java.util.List;

public record CandidateRepositoryPageResponse(
    List<CandidateRepositoryResponse> content,
    int page,
    int perPage,
    boolean hasNext
) {
    public static CandidateRepositoryPageResponse from(CandidateRepositoryListResult result) {
        return new CandidateRepositoryPageResponse(
            result.content().stream().map(CandidateRepositoryResponse::from).toList(),
            result.page(), result.perPage(), result.hasNext()
        );
    }
}
