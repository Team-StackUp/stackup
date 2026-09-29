package com.stackup.stackup.profile.domain;

import com.stackup.stackup.session.domain.JobCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 취업 프로필 — 희망 직군 · 희망 산업 · 경력 수준.
 *
 * <p>왜 별도 슬라이스인가: {@code User} 에 직접 달면 user 가 {@link JobCategory}(session
 * 슬라이스)를 참조해 <b>user→session→user 순환</b>이 생기고 ArchUnit 이 막는다
 * (OAuthProvider 를 user.domain 에 둔 것과 같은 이유). profile 이 user·session 을 의존하고
 * 역방향이 없으므로 순환이 없다.
 *
 * <p>왜 필요한가: 면접을 만들 때마다 직군을 다시 고르게 했고, 직군만으로는 질문 맥락이
 * 얇았다 — 같은 '생산·품질' 이라도 <b>반도체 공정과 건설 현장은 묻는 것이 전혀 다르다.</b>
 *
 * <p>모든 항목이 선택값이다. 프로필을 안 채운 사용자도 지금처럼 면접을 만들 수 있어야 한다.
 */
@Getter
@Entity
@Table(name = "user_job_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJobProfile {

    /** users.id 를 그대로 PK 로 쓴다(1:1). 별도 시퀀스를 둘 이유가 없다. */
    @Id
    @Column(name = "user_id")
    private Long userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_job_profile_categories",
        joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "job_category", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<JobCategory> desiredJobCategories = new LinkedHashSet<>();

    /**
     * 희망 산업. <b>자유 입력이다</b> — 열거하면 빠진 산업의 지원자가 또 배제된다
     * (반도체·건설·토목을 넣어도 조선·방산·바이오…). 평가 관점을 큐레이션해야 하는 직군과
     * 달리 프롬프트 맥락으로만 쓰이므로 제약이 필요 없다.
     */
    @Column(name = "desired_industry", length = 100)
    private String desiredIndustry;

    @Enumerated(EnumType.STRING)
    @Column(name = "career_level", length = 20)
    private CareerLevel careerLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public static UserJobProfile empty(Long userId) {
        UserJobProfile p = new UserJobProfile();
        p.userId = userId;
        return p;
    }

    /**
     * 프로필 갱신.
     *
     * <p>null 은 "지우기" 가 아니라 <b>"그대로 두기"</b> 다 — 계정 화면이 일부 항목만 보내도
     * 나머지가 날아가면 안 된다. 산업을 비우려면 빈 문자열을 보낸다(직군은 빈 목록).
     */
    public void update(
        Set<JobCategory> desiredJobCategories,
        String desiredIndustry,
        CareerLevel careerLevel
    ) {
        if (desiredJobCategories != null) {
            this.desiredJobCategories = new LinkedHashSet<>(desiredJobCategories);
        }
        if (desiredIndustry != null) {
            String trimmed = desiredIndustry.trim();
            this.desiredIndustry = trimmed.isEmpty() ? null : trimmed;
        }
        if (careerLevel != null) {
            this.careerLevel = careerLevel;
        }
        this.updatedAt = Instant.now();
    }

    /** 한 번이라도 채웠는가. 가입 직후 안내를 띄울지 판단한다. */
    public boolean isFilled() {
        return !desiredJobCategories.isEmpty() || desiredIndustry != null || careerLevel != null;
    }
}
