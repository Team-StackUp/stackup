package com.stackup.stackup.profile.application.dto;

import com.stackup.stackup.profile.domain.CareerLevel;
import com.stackup.stackup.session.domain.JobCategory;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 프로필 갱신 요청. 전부 선택값이고 <b>null 은 "그대로 두기"</b> 다 — 계정 화면이 일부만
 * 보내도 나머지가 날아가면 안 된다. 비우려면 빈 값(빈 목록 / 빈 문자열)을 보낸다.
 */
public record JobProfileUpdateRequest(
    List<JobCategory> desiredJobCategories,

    // 자유 입력이라 길이만 막는다. 산업명은 길어야 수십 자다.
    @Size(max = 100, message = "희망 산업은 100자를 넘을 수 없습니다.")
    String desiredIndustry,

    CareerLevel careerLevel
) {}
