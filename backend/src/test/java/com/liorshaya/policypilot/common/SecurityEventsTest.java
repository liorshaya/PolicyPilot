package com.liorshaya.policypilot.common;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/** Security events: a counter and a structured log line each (Document 5, Security Logging and Monitoring). */
class SecurityEventsTest {

    private static final String IP = "203.0.113.7";
    private static final UUID SANDBOX = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a00");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SecurityEvents events = new SecurityEvents(registry, "salt-one".getBytes(StandardCharsets.UTF_8));
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void captureLogs() {
        logger = (Logger) LoggerFactory.getLogger(SecurityEvents.class);
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    // Expected: the counter names of Document 5's event table, the seven that day 4 can raise
    @ParameterizedTest
    @ValueSource(strings = {
        "security.auth.failed", "security.auth.lockout", "security.session.invalid", "security.authz.denied",
        "security.ratelimit.hit", "security.input.rejected", "security.protected.write_attempt"})
    void eachDayFourEventIncrementsItsNamedCounter(String counter) {
        raiseEveryEventOnce();

        assertThat(registry.find(counter).counters()).singleElement()
                .extracting(c -> c.count()).isEqualTo(1.0);
    }

    // Document 5, Security logging: "Tool call rejected | Tool name, reason | ai.tool.rejected". Expected: the counter
    // tagged with both, and one log line that names them
    @Test
    void aRejectedToolCallIsCountedByToolAndReason() {
        events.toolRejected("simulate", "not_found");

        assertThat(registry.counter("ai.tool.rejected", "tool", "simulate", "reason", "not_found").count())
                .isEqualTo(1.0);
        assertThat(mine()).extracting(ILoggingEvent::getMessage).containsExactly("ai.tool.rejected");
    }

    // Document 5, Security logging: "Denylist hit in a stream | Prompt version, pattern class". Expected: the counter
    // tagged with both, and one log line that carries no text of the answer
    @Test
    void aDenylistHitIsCountedByPromptAndPatternClass() {
        events.outputDenylisted("answer/v1", "secret");

        assertThat(registry.counter("security.output.denylist", "prompt", "answer/v1", "pattern", "secret")
                .count()).isEqualTo(1.0);
        assertThat(mine()).extracting(ILoggingEvent::getMessage).containsExactly("security.output.denylist");
    }

    // Document 5, Security logging: "Injection finding | Rule set id, paragraph, kind | ai.finding.injection".
    // Expected: the counter, and one log line with the three keys and no text of the passage
    @Test
    void anInjectionFindingIsCountedAndLoggedByRuleSetParagraphAndKind() {
        UUID ruleset = UUID.fromString("0f4c1c9e-0000-4000-8000-000000000001");

        events.injectionFound(ruleset, 8);

        assertThat(registry.counter("ai.finding.injection").count()).isEqualTo(1.0);
        ILoggingEvent line = mine().getFirst();
        assertThat(line.getMessage()).isEqualTo("ai.finding.injection");
        assertThat(line.getKeyValuePairs()).extracting(pair -> pair.key + "=" + pair.value)
                .containsExactly("ruleset=" + ruleset, "paragraph=8", "kind=injection");
    }

    @Test
    void everyEventWritesOneLogLineNamedAfterItsCounter() {
        raiseEveryEventOnce();

        assertThat(mine()).extracting(ILoggingEvent::getMessage).containsExactly(
                "security.auth.failed", "security.auth.lockout", "security.session.invalid",
                "security.authz.denied", "security.ratelimit.hit", "security.input.rejected",
                "security.protected.write_attempt");
    }

    @Test
    void ipAddressIsLoggedHashedNeverRaw() {
        events.authFailed(IP, 3, false);
        events.rateLimitHit("auth", IP);

        assertThat(mine()).flatExtracting(ILoggingEvent::getKeyValuePairs).extracting(pair -> String.valueOf(pair.value))
                .doesNotContain(IP).contains(events.hash(IP));
    }

    @Test
    void requestedIdIsLoggedHashed() {
        events.authorizationDenied("policy", SANDBOX, "0f4c1c9e-0000-4000-8000-000000000001");

        assertThat(mine().getFirst().getKeyValuePairs()).extracting(pair -> String.valueOf(pair.value))
                .doesNotContain("0f4c1c9e-0000-4000-8000-000000000001");
    }

    @Test
    void ipHashIsStableWithinADeploymentAndDiffersAcrossSalts() {
        SecurityEvents otherDeployment = new SecurityEvents(registry, "salt-two".getBytes(StandardCharsets.UTF_8));

        assertThat(events.hash(IP)).isEqualTo(events.hash(IP)).hasSize(16).isNotEqualTo(otherDeployment.hash(IP));
    }

    // Expected: RFC 4231 test case 2, HMAC-SHA256("Jefe", "what do ya want for nothing?"), first 16 hex digits
    @Test
    void hashIsTheTruncatedHmacSha256() {
        SecurityEvents rfc = new SecurityEvents(registry, "Jefe".getBytes(StandardCharsets.UTF_8));

        assertThat(rfc.hash("what do ya want for nothing?")).isEqualTo("5bdcc146bf60754e");
    }

    @Test
    void inputRejectionLogsTheCodeNeverTheValue() {
        events.inputRejected("POST /api/v1/policies", "POLICY_INVALID");

        assertThat(mine().getFirst().getKeyValuePairs()).extracting(pair -> pair.key)
                .containsExactly("endpoint", "code");
    }

    /**
     * The lines this test wrote: other test classes run in parallel and log security events through the same logger,
     * and a test method runs on one thread.
     */
    private List<ILoggingEvent> mine() {
        String thread = Thread.currentThread().getName();
        return logs.list.stream().filter(event -> event.getThreadName().equals(thread)).toList();
    }

    private void raiseEveryEventOnce() {
        events.authFailed(IP, 1, false);
        events.lockoutStarted(IP, Duration.ofMinutes(15));
        events.sessionInvalid("bad-signature");
        events.authorizationDenied("policy", SANDBOX, "id");
        events.rateLimitHit("other", IP);
        events.inputRejected("POST /api/v1/policies", "POLICY_INVALID");
        events.protectedWriteAttempt("policy", SANDBOX);
    }
}
