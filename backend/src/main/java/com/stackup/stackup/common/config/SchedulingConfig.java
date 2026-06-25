package com.stackup.stackup.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// 스케줄링 활성화. 현재 사용처: 시간 초과 IN_PROGRESS 세션 자동 종료(SessionTimeoutSweeper).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
