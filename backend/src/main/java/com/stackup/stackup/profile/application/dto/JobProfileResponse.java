package com.stackup.stackup.profile.application.dto;

import com.stackup.stackup.profile.domain.CareerLevel;
import com.stackup.stackup.profile.domain.UserJobProfile;
import com.stackup.stackup.session.domain.JobCategory;
import java.util.List;
import java.util.Set;

public record JobProfileResponse(
    List<JobCategory> desiredJobCategories,
    String desiredIndustry,
    CareerLevel careerLevel,
    boolean filled
) {
    public static JobProfileResponse from(UserJobProfile profile) {
        Set<JobCategory> categories = profile.getDesiredJobCategories();
        return new JobProfileResponse(
            List.copyOf(categories),
            profile.getDesiredIndustry(),
            profile.getCareerLevel(),
            profile.isFilled()
        );
    }

    /** 프로필을 만든 적 없는 사용자. 404 대신 빈 프로필을 준다 — 없는 것도 정상 상태다. */
    public static JobProfileResponse empty() {
        return new JobProfileResponse(List.of(), null, null, false);
    }
}
