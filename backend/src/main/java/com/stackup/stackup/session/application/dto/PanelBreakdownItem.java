package com.stackup.stackup.session.application.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

// 멀티 면접관 패널의 평가위원 1명 결과(분해). AI 콜백 camelCase 와 1:1, 응답에도 그대로 노출.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PanelBreakdownItem(
    String evaluator,
    String dimension,
    Double score,
    String strength,
    String weakness
) {
}
