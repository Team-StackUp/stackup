package com.stackup.stackup.session.presentation.dto;

import com.stackup.stackup.session.application.dto.SessionCreateCommand;
import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.session.domain.SessionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SessionCreateRequest(
    @Size(max = 200) String title,
    @Size(max = 4000) String memo,
    // mode: TECHNICAL/PERSONALITY/INTEGRATED 중 하나를 선택한다.
    @NotNull SessionMode mode,
    @NotNull JobCategory jobCategory,
    // 첫 질문 + 꼬리질문 최소 1쌍 보장을 위해 하한 2. applyFollowup 자동 종료 흐름 결합.
    @Min(2) @Max(30) Integer maxQuestions,
    // maxDurationMinutes: 시간 기반 자동 종료 미구현. DB 저장만 유지, 입력 검증 풀어 미입력 허용.
    Integer maxDurationMinutes,
    List<Long> contextDocumentIds
) {
    public SessionCreateCommand toCommand() {
        return new SessionCreateCommand(
            title,
            memo,
            mode,
            jobCategory,
            maxQuestions,
            maxDurationMinutes,
            contextDocumentIds
        );
    }
}
