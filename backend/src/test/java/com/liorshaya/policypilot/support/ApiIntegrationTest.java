package com.liorshaya.policypilot.support;

import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class of the API integration tests: the whole application on a random port against the shared PostgreSQL
 * container, one Spring context for every subclass, a clock that stands still at {@link #START}, and the client IP
 * taken from {@code X-Forwarded-For} as behind Railway's proxy, and the {@link FakeEmbeddingGateway} as the embedding
 * provider. A subclass that moves the clock is {@code @Isolated}; the clock goes back to {@link #START} after every
 * test.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = {
    "spring.ai.openai.api-key=test-key-not-real",
    "server.forward-headers-strategy=native",
    "server.address=" + Api.HOST,
    // every distinct test context keeps its own pool against the one container, whose limit is 100 connections
    "spring.datasource.hikari.maximum-pool-size=5"})
@ActiveProfiles("openai")
@Import({ApiIntegrationTest.FixedClock.class, OfflineEmbeddings.class})
public abstract class ApiIntegrationTest extends PostgresContainerSupport {

    public static final Instant START = Instant.parse("2026-09-24T09:00:00Z");

    @LocalServerPort
    protected int port;

    @Autowired
    protected MutableClock clock;

    protected Api api() {
        return new Api(port);
    }

    @AfterEach
    void resetClock() {
        clock.set(START);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }
}
