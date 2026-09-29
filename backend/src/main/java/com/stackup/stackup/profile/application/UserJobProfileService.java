package com.stackup.stackup.profile.application;

import com.stackup.stackup.profile.application.dto.JobProfileResponse;
import com.stackup.stackup.profile.application.dto.JobProfileUpdateRequest;
import com.stackup.stackup.profile.domain.UserJobProfile;
import com.stackup.stackup.profile.domain.UserJobProfileRepository;
import com.stackup.stackup.session.domain.JobCategory;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserJobProfileService {

    private final UserJobProfileRepository repository;

    /** 프로필이 없으면 빈 것을 돌려준다 — "아직 안 채움" 은 오류가 아니다. */
    public JobProfileResponse get(Long userId) {
        return repository.findByUserId(userId)
            .map(JobProfileResponse::from)
            .orElseGet(JobProfileResponse::empty);
    }

    @Transactional
    public JobProfileResponse update(Long userId, JobProfileUpdateRequest request) {
        UserJobProfile profile = repository.findByUserId(userId)
            .orElseGet(() -> UserJobProfile.empty(userId));

        Set<JobCategory> categories = request.desiredJobCategories() == null
            ? null
            : new LinkedHashSet<>(request.desiredJobCategories());

        profile.update(categories, request.desiredIndustry(), request.careerLevel());
        return JobProfileResponse.from(repository.save(profile));
    }
}
