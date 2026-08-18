package com.stackup.stackup.resume.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// 웹 이력서(URL) 등록 요청. 스킴·호스트 검증은 WebResumeUrlValidator(SSRF 가드)가 담당한다.
public record WebResumeCreateRequest(
    @NotBlank @Size(max = 2000) String url
) {
}
