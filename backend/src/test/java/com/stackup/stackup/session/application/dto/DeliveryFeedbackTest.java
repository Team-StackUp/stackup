package com.stackup.stackup.session.application.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DeliveryFeedbackTest {

    // 약 60어절 분량의 답변(무음 비율·간투어 밀도 정규화의 분모).
    private static final String ANSWER = "저는 백엔드 개발자로서 "
        + "대용량 트래픽을 처리하는 결제 시스템을 설계하고 운영한 경험이 있습니다 "
        + "특히 데이터베이스 인덱스를 튜닝하고 캐시 계층을 도입해 응답 속도를 크게 "
        + "개선했으며 장애 상황에서도 안정적으로 동작하도록 모니터링과 알림 체계를 "
        + "구축했습니다 또한 동료들과 코드 리뷰 문화를 정착시켜 품질을 높였습니다 "
        + "앞으로도 견고한 시스템을 만드는 개발자가 되고 싶습니다 감사합니다";

    @Test
    void noMetrics_returnsNull() {
        assertThat(DeliveryFeedback.assess(ANSWER, null, null, null, null)).isNull();
        assertThat(DeliveryFeedback.assess(ANSWER, null, null, Map.of(), null)).isNull();
    }

    @Test
    void cleanDelivery_isGood() {
        DeliveryFeedback fb = DeliveryFeedback.assess(ANSWER, 120.0, 4.0, Map.of(), 0.95);
        assertThat(fb).isNotNull();
        assertThat(fb.rating()).isEqualTo("GOOD");
        assertThat(fb.comment()).contains("안정");
    }

    @Test
    void fastPace_isFlagged() {
        DeliveryFeedback fb = DeliveryFeedback.assess(ANSWER, 180.0, 4.0, Map.of(), 0.95);
        assertThat(fb.rating()).isEqualTo("FAIR");
        assertThat(fb.comment()).contains("빠른");
    }

    @Test
    void multipleIssues_arePoor() {
        // 빠른 속도 + 잦은 간투어 + 낮은 발음 신뢰도 → 2건 이상 → POOR.
        DeliveryFeedback fb = DeliveryFeedback.assess(
            ANSWER, 185.0, 4.0, Map.of("어", 6, "그", 5), 0.70);
        assertThat(fb.rating()).isEqualTo("POOR");
        assertThat(fb.comment()).contains("간투어");
    }

    @Test
    void normalFillerDensity_notFlagged() {
        // ~60어절에 간투어 1회 → 100어절당 ~1.7개로 임계(3) 미만 → 미지적.
        DeliveryFeedback fb = DeliveryFeedback.assess(ANSWER, 120.0, 4.0, Map.of("어", 1), 0.95);
        assertThat(fb.rating()).isEqualTo("GOOD");
    }
}
