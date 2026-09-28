package com.stackup.stackup.common.trace;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

/**
 * 필터 순서가 틀리면 **인증 실패 로그에 traceId 가 없다.**
 *
 * <p>@Order 가 없으면 Spring Boot 는 LOWEST_PRECEDENCE 로 등록하는데 Spring Security 체인은
 * -100 이라 보안 체인이 먼저 돈다. 인증 실패는 보안 체인 안에서 끝나므로 TraceIdFilter 에
 * 닿지 못한다. 운영 로그에 실제로 그렇게 찍혀 있었다:
 * {@code Authentication failed. code=AUTH_INVALID_TOKEN, traceId=null, uri=/api/.env}
 *
 * <p>추적 ID 가 가장 필요한 순간(인증 실패·공격 스캔)에 정확히 빠져 있었고, 이 순서는
 * 애노테이션 한 줄이 지워져도 컴파일·테스트가 통과하며 <b>운영에서도 조용하다</b>.
 */
class TraceIdFilterOrderTest {

    @Test
    void 보안_필터체인보다_먼저_돈다() {
        Order order = AnnotationUtils.findAnnotation(TraceIdFilter.class, Order.class);

        assertThat(order)
            .as("@Order 가 없으면 Spring Security(-100) 뒤로 밀려 인증 실패에 traceId 가 없다")
            .isNotNull();
        assertThat(order.value())
            .as("Spring Security 기본 필터 순서(-100)보다 앞서야 한다")
            .isEqualTo(Ordered.HIGHEST_PRECEDENCE)
            .isLessThan(-100);
    }
}
