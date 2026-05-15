package com.stackup.stackup.common.config;

import java.security.SecureRandom;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityBeanConfig {

    @Bean
    public SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
