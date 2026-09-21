package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Puts {@link FakeEmbeddingGateway} in place of the provider in a Spring test context. Every context needs it: the
 * embedding job runs at startup for the seeded version, and a test never reaches the network (Document 6). The fake
 * has the profile's dimension, so the startup check compares it with the column exactly as it would the real one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OfflineEmbeddings {

    @Bean
    @Primary
    FakeEmbeddingGateway fakeEmbeddingGateway(PolicyPilotProperties properties) {
        return new FakeEmbeddingGateway(properties.embedding().dimension());
    }
}
