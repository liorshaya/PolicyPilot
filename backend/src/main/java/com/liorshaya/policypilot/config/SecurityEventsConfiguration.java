package com.liorshaya.policypilot.config;

import com.liorshaya.policypilot.common.Hmac;
import com.liorshaya.policypilot.common.SecurityEvents;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The security events with their hash key: an HMAC of a fixed label under the cookie secret, so the per-deployment
 * salt of Document 5 (Redaction) needs no variable of its own and changes when the secret is rotated.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityEventsConfiguration {

    static final String HASH_KEY_LABEL = "policypilot/log-hash/v1";

    @Bean
    SecurityEvents securityEvents(MeterRegistry registry, PolicyPilotProperties properties) {
        byte[] secret = properties.cookieSecret().getBytes(StandardCharsets.UTF_8);
        return new SecurityEvents(registry, Hmac.sha256(secret, HASH_KEY_LABEL));
    }
}
