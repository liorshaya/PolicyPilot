package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The SSE connection cap (Document 5, limits table: 3 open per sandbox, 60 s idle timeout, 5 minutes maximum). */
class StreamRegistryTest {

    private static final Instant T0 = Instant.parse("2026-09-24T09:00:00Z");
    private static final UUID SANDBOX = UUID.fromString("7f1c0e7a-1111-4222-8333-944445555666");

    private final StreamRegistry registry = new StreamRegistry(3);

    @Test
    void threeStreamsOfASandboxMayBeOpen() {
        assertThat(registry.open(SANDBOX, T0)).isPresent();
        assertThat(registry.open(SANDBOX, T0)).isPresent();
        assertThat(registry.open(SANDBOX, T0)).isPresent();
        assertThat(registry.openStreams(SANDBOX)).isEqualTo(3);
    }

    @Test
    void fourthConcurrentStreamOfASandboxIsRefused() {
        openThree();

        assertThat(registry.open(SANDBOX, T0)).isEmpty();
    }

    @Test
    void closingAStreamFreesItsSlot() {
        StreamRegistry.Lease first = openThree();

        first.close();
        first.close();

        assertThat(registry.open(SANDBOX, T0)).isPresent();
        assertThat(registry.openStreams(SANDBOX)).isEqualTo(3);
    }

    @Test
    void sandboxesDoNotShareSlots() {
        openThree();

        assertThat(registry.open(UUID.randomUUID(), T0)).isPresent();
    }

    @Test
    void idleStreamIsClosedAfter60Seconds() {
        StreamRegistry.Lease lease = registry.open(SANDBOX, T0).orElseThrow();

        assertThat(registry.expire(T0.plusSeconds(59))).isEmpty();
        assertThat(registry.expire(T0.plusSeconds(60))).containsExactly(lease);
        assertThat(registry.openStreams(SANDBOX)).isZero();
    }

    @Test
    void activityKeepsAStreamOpen() {
        StreamRegistry.Lease lease = registry.open(SANDBOX, T0).orElseThrow();
        lease.touch(T0.plusSeconds(50));

        assertThat(registry.expire(T0.plusSeconds(100))).isEmpty();
    }

    @Test
    void streamIsClosedAfterFiveMinutesEvenWhenActive() {
        StreamRegistry.Lease lease = registry.open(SANDBOX, T0).orElseThrow();
        for (int second = 30; second < 300; second += 30) {
            lease.touch(T0.plusSeconds(second));
        }

        assertThat(registry.expire(T0.plus(Duration.ofMinutes(5)).minusSeconds(1))).isEmpty();
        assertThat(registry.expire(T0.plus(Duration.ofMinutes(5)))).containsExactly(lease);
    }

    @Test
    void anExpiredStreamFreesItsSlot() {
        openThree();
        registry.expire(T0.plusSeconds(60));

        assertThat(registry.open(SANDBOX, T0.plusSeconds(60))).isPresent();
    }

    @Test
    void aLeaseKnowsItsSandbox() {
        assertThat(registry.open(SANDBOX, T0).orElseThrow().sandboxId()).isEqualTo(SANDBOX);
    }

    @Test
    void anUnknownSandboxHasNoOpenStreams() {
        assertThat(registry.openStreams(UUID.randomUUID())).isZero();
    }

    private StreamRegistry.Lease openThree() {
        StreamRegistry.Lease first = registry.open(SANDBOX, T0).orElseThrow();
        registry.open(SANDBOX, T0);
        registry.open(SANDBOX, T0);
        return first;
    }
}
