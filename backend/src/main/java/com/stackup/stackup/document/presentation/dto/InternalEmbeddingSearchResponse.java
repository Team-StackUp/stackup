package com.stackup.stackup.document.presentation.dto;

import com.stackup.stackup.document.application.dto.EmbeddingSearchResult;
import java.util.List;

public record InternalEmbeddingSearchResponse(List<Result> results) {

    public static InternalEmbeddingSearchResponse from(List<EmbeddingSearchResult> results) {
        return new InternalEmbeddingSearchResponse(
                results.stream()
                        .map(Result::from)
                        .toList()
        );
    }

    public record Result(
            long documentId,
            int chunkIndex,
            String chunkText,
            double score,
            String model
    ) {

        private static Result from(EmbeddingSearchResult result) {
            return new Result(
                    result.documentId(),
                    result.chunkIndex(),
                    result.chunkText(),
                    result.score(),
                    result.model()
            );
        }
    }
}
