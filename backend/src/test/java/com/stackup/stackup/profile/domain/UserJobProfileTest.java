package com.stackup.stackup.profile.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stackup.stackup.session.domain.JobCategory;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserJobProfileTest {

    @Test
    void 생략한_항목은_그대로_둔다() {
        // 계정 화면이 일부만 보내도 나머지가 날아가면 안 된다.
        UserJobProfile profile = UserJobProfile.empty(1L);
        profile.update(Set.of(JobCategory.SALES), "반도체", CareerLevel.NEW);

        profile.update(null, null, null);

        assertThat(profile.getDesiredJobCategories()).containsExactly(JobCategory.SALES);
        assertThat(profile.getDesiredIndustry()).isEqualTo("반도체");
        assertThat(profile.getCareerLevel()).isEqualTo(CareerLevel.NEW);
    }

    @Test
    void 빈_문자열은_지우기다() {
        UserJobProfile profile = UserJobProfile.empty(1L);
        profile.update(null, "건설·토목", null);

        profile.update(null, "  ", null);

        // null 이 "그대로 두기" 라서, 비우는 수단이 따로 있어야 한다.
        assertThat(profile.getDesiredIndustry()).isNull();
    }

    @Test
    void 산업은_앞뒤_공백을_떼고_저장한다() {
        UserJobProfile profile = UserJobProfile.empty(1L);

        profile.update(null, "  반도체 ", null);

        assertThat(profile.getDesiredIndustry()).isEqualTo("반도체");
    }

    @Test
    void 빈_목록으로_직군을_비울_수_있다() {
        UserJobProfile profile = UserJobProfile.empty(1L);
        profile.update(Set.of(JobCategory.HR), null, null);

        profile.update(Set.of(), null, null);

        assertThat(profile.getDesiredJobCategories()).isEmpty();
    }

    @Test
    void 아무것도_안_채우면_filled_가_거짓이다() {
        // 가입 직후 안내를 띄울지 판단하는 값이다.
        assertThat(UserJobProfile.empty(1L).isFilled()).isFalse();

        UserJobProfile onlyIndustry = UserJobProfile.empty(2L);
        onlyIndustry.update(null, "금융", null);
        assertThat(onlyIndustry.isFilled()).isTrue();
    }
}
