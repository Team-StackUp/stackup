package com.stackup.stackup.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.stackup.stackup.profile.application.dto.JobProfileResponse;
import com.stackup.stackup.profile.application.dto.JobProfileUpdateRequest;
import com.stackup.stackup.profile.domain.CareerLevel;
import com.stackup.stackup.profile.domain.UserJobProfile;
import com.stackup.stackup.profile.domain.UserJobProfileRepository;
import com.stackup.stackup.session.domain.JobCategory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserJobProfileServiceTest {

    @Mock UserJobProfileRepository repository;
    @InjectMocks UserJobProfileService service;

    @Test
    void 프로필이_없으면_404_가_아니라_빈_프로필이다() {
        // "아직 안 채움" 은 오류가 아니다 — 프로필 없이도 면접은 만들 수 있어야 한다.
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());

        JobProfileResponse response = service.get(1L);

        assertThat(response.filled()).isFalse();
        assertThat(response.desiredJobCategories()).isEmpty();
        assertThat(response.desiredIndustry()).isNull();
    }

    @Test
    void 처음_저장하면_새로_만든다() {
        when(repository.findByUserId(1L)).thenReturn(Optional.empty());
        when(repository.save(any(UserJobProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        JobProfileResponse response = service.update(1L, new JobProfileUpdateRequest(
            List.of(JobCategory.MANUFACTURING), "반도체", CareerLevel.NEW));

        assertThat(response.desiredJobCategories()).containsExactly(JobCategory.MANUFACTURING);
        assertThat(response.desiredIndustry()).isEqualTo("반도체");
        assertThat(response.careerLevel()).isEqualTo(CareerLevel.NEW);
        assertThat(response.filled()).isTrue();
    }

    @Test
    void 일부만_보내면_나머지는_유지된다() {
        UserJobProfile existing = UserJobProfile.empty(1L);
        existing.update(List.of(JobCategory.SALES).stream().collect(
            java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new)),
            "금융", CareerLevel.EXPERIENCED);
        when(repository.findByUserId(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any(UserJobProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        JobProfileResponse response = service.update(1L,
            new JobProfileUpdateRequest(null, null, CareerLevel.CAREER_CHANGE));

        assertThat(response.desiredJobCategories()).containsExactly(JobCategory.SALES);
        assertThat(response.desiredIndustry()).isEqualTo("금융");
        assertThat(response.careerLevel()).isEqualTo(CareerLevel.CAREER_CHANGE);
    }
}
