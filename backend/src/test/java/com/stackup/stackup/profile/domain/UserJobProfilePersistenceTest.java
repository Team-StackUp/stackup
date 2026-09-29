package com.stackup.stackup.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.session.domain.JobCategory;
import com.stackup.stackup.support.PostgresRepositoryTest;
import com.stackup.stackup.user.domain.User;
import com.stackup.stackup.user.domain.UserRepository;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * V36 마이그레이션과 매핑이 실제 PostgreSQL 에서 맞는지 본다.
 *
 * <p>엔티티만 맞고 마이그레이션이 틀리면 그 실패는 <b>사용자가 프로필을 저장하는 순간</b>에만
 * 드러난다. 산업에 CHECK 를 걸지 않은 것도 여기서 확인한다 — 목록 밖 값이 막히면 "자유 입력"
 * 이라고 해 놓고 실제로는 막는 것이 된다.
 */
@PostgresRepositoryTest
class UserJobProfilePersistenceTest {

    @Autowired UserRepository userRepository;
    @Autowired UserJobProfileRepository profileRepository;

    private Long newUserId(long githubId, String name) {
        return userRepository.save(User.createGithubUser(githubId, name, null, null, "t")).getId();
    }

    @Test
    void 프로필이_저장되고_다시_읽힌다() {
        Long userId = newUserId(96001L, "profile-user");

        UserJobProfile profile = UserJobProfile.empty(userId);
        profile.update(new LinkedHashSet<>(Set.of(JobCategory.MANUFACTURING)), "반도체",
            CareerLevel.NEW);
        profileRepository.saveAndFlush(profile);

        UserJobProfile found = profileRepository.findByUserId(userId).orElseThrow();
        assertThat(found.getDesiredJobCategories()).containsExactly(JobCategory.MANUFACTURING);
        assertThat(found.getDesiredIndustry()).isEqualTo("반도체");
        assertThat(found.getCareerLevel()).isEqualTo(CareerLevel.NEW);
    }

    @Test
    void 제안_목록에_없는_산업도_그대로_저장된다() {
        // 산업에 CHECK 를 걸지 않은 이유 그 자체 — 열거하면 빠진 산업의 지원자가 배제된다.
        Long userId = newUserId(96002L, "rare-industry");

        UserJobProfile profile = UserJobProfile.empty(userId);
        profile.update(null, "수직농업 스마트팜", null);
        profileRepository.saveAndFlush(profile);

        assertThat(profileRepository.findByUserId(userId).orElseThrow().getDesiredIndustry())
            .isEqualTo("수직농업 스마트팜");
    }

    @Test
    void 개발_비개발_직군을_함께_저장할_수_있다() {
        Long userId = newUserId(96003L, "mixed-profile");

        UserJobProfile profile = UserJobProfile.empty(userId);
        profile.update(new LinkedHashSet<>(
            java.util.List.of(JobCategory.BACKEND, JobCategory.PLANNING)), null, null);
        profileRepository.saveAndFlush(profile);

        assertThat(profileRepository.findByUserId(userId).orElseThrow().getDesiredJobCategories())
            .containsExactlyInAnyOrder(JobCategory.BACKEND, JobCategory.PLANNING);
    }

    @Test
    void 모든_경력_수준이_CHECK_를_통과한다() {
        long id = 96100L;
        for (CareerLevel level : CareerLevel.values()) {
            Long userId = newUserId(id++, "career-" + level.name());
            UserJobProfile profile = UserJobProfile.empty(userId);
            profile.update(null, null, level);
            profileRepository.saveAndFlush(profile);
        }

        assertThat(profileRepository.count()).isEqualTo(CareerLevel.values().length);
    }
}
