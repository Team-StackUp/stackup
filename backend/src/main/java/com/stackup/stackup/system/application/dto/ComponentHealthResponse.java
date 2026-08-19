package com.stackup.stackup.system.application.dto;

// 공개(permitAll) 엔드포인트의 응답이므로 컴포넌트 이름과 상태만 담는다.
// 상세(버전·버킷·큐·적체량)는 인증 없이 흘리면 안 되고, 필요하면 호스트에서
// Spring 자체 /actuator/health 를 본다(nginx 가 외부로 라우팅하지 않는다).
public record ComponentHealthResponse(
    String name,
    String status
) {
}
