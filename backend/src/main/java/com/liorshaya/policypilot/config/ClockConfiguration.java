package com.liorshaya.policypilot.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The API's clock, a bean so tests can fix it (Document 6, Determinism and isolation): cookie expiry, the lockout
 * and the rate-limit windows read it. The engine has no clock at all.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
