package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The code-exchange lockout (Document 5, Brute force: 20 failures within 15 minutes lock the IP for 15 minutes; the
 * correct code is refused too while locked). Times are explicit instants; nothing reads a clock.
 */
class LoginThrottleTest {

    private static final String IP = "198.51.100.1";
    private static final Instant T0 = Instant.parse("2026-09-24T09:00:00Z");

    private final LoginThrottle throttle = new LoginThrottle();

    @Test
    void nineteenFailuresDoNotLock() {
        LoginThrottle.Failure last = fail(19, T0);

        assertThat(last).isEqualTo(new LoginThrottle.Failure(19, false));
        assertThat(throttle.lockedFor(IP, T0)).isEmpty();
    }

    @Test
    void twentiethFailureLocksForFifteenMinutes() {
        LoginThrottle.Failure last = fail(20, T0);

        assertThat(last).isEqualTo(new LoginThrottle.Failure(20, true));
        assertThat(throttle.lockedFor(IP, T0)).contains(Duration.ofMinutes(15));
    }

    @Test
    void lockLiftsExactlyFifteenMinutesAfterItStarted() {
        fail(20, T0);

        assertThat(throttle.lockedFor(IP, T0.plus(Duration.ofMinutes(15)).minusSeconds(1))).contains(Duration.ofSeconds(1));
        assertThat(throttle.lockedFor(IP, T0.plus(Duration.ofMinutes(15)))).isEmpty();
    }

    @Test
    void failuresOlderThanFifteenMinutesAreForgotten() {
        fail(19, T0);

        LoginThrottle.Failure next = throttle.recordFailure(IP, T0.plus(Duration.ofMinutes(15)));

        assertThat(next).isEqualTo(new LoginThrottle.Failure(1, false));
    }

    @Test
    void failuresJustInsideTheWindowStillCount() {
        fail(19, T0);

        LoginThrottle.Failure next = throttle.recordFailure(IP, T0.plus(Duration.ofMinutes(15)).minusSeconds(1));

        assertThat(next).isEqualTo(new LoginThrottle.Failure(20, true));
    }

    @Test
    void successClearsTheFailures() {
        fail(19, T0);
        throttle.recordSuccess(IP);

        assertThat(throttle.recordFailure(IP, T0)).isEqualTo(new LoginThrottle.Failure(1, false));
    }

    @Test
    void afterALockoutTheCountStartsAgain() {
        fail(20, T0);

        LoginThrottle.Failure next = throttle.recordFailure(IP, T0.plus(Duration.ofMinutes(15)));

        assertThat(next).isEqualTo(new LoginThrottle.Failure(1, false));
    }

    @Test
    void ipsAreIndependent() {
        fail(20, T0);

        assertThat(throttle.lockedFor("198.51.100.2", T0)).isEmpty();
    }

    @Test
    void unknownIpIsNotLocked() {
        assertThat(throttle.lockedFor("203.0.113.9", T0)).isEmpty();
    }

    @Test
    void idleClientsArePurgedOnceTheMapGrowsPastItsThreshold() {
        for (int i = 0; i <= LoginThrottle.PURGE_THRESHOLD; i++) {
            throttle.recordFailure("10.0." + (i / 256) + "." + (i % 256), T0);
        }
        fail(20, T0);

        throttle.recordFailure("192.0.2.1", T0.plus(Duration.ofMinutes(16)));

        assertThat(throttle.trackedClients()).isEqualTo(1);
    }

    @Test
    void purgeKeepsALockedClient() {
        fail(20, T0);
        for (int i = 0; i <= LoginThrottle.PURGE_THRESHOLD; i++) {
            throttle.recordFailure("10.0." + (i / 256) + "." + (i % 256), T0);
        }

        throttle.recordFailure("192.0.2.1", T0.plus(Duration.ofMinutes(14)));

        assertThat(throttle.lockedFor(IP, T0.plus(Duration.ofMinutes(14)))).isPresent();
    }

    private LoginThrottle.Failure fail(int times, Instant at) {
        LoginThrottle.Failure last = null;
        for (int i = 0; i < times; i++) {
            last = throttle.recordFailure(IP, at);
        }
        return last;
    }
}
