package com.stackup.stackup.resume.presentation.dto;

import com.stackup.stackup.resume.application.dto.ResumeResult;
import java.util.List;
import org.springframework.data.domain.Page;

public record ResumePageResponse(
    List<ResumeResponse> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
    public static ResumePageResponse from(Page<ResumeResult> page) {
        return new ResumePageResponse(
            page.getContent().stream().map(ResumeResponse::from).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isFirst(),
            page.isLast()
        );
    }
}
