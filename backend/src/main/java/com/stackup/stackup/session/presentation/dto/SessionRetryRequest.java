package com.stackup.stackup.session.presentation.dto;

// 재도전 옵션. 본문 없이 호출하면 focusOnWeakness=false 와 같다(설정만 복사하는 일반 재도전).
public record SessionRetryRequest(
    // true 면 원본 세션 피드백에서 낮았던 평가 축을 새 세션의 집중 영역으로 새긴다.
    Boolean focusOnWeakness
) {
    public boolean focusOnWeaknessOrDefault() {
        return Boolean.TRUE.equals(focusOnWeakness);
    }
}
