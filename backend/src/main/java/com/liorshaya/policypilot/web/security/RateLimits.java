package com.liorshaya.policypilot.web.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.EstimationProbe;
import io.github.bucket4j.TimeMeter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The request rate limits of Document 5 (Availability and Abuse Resistance), as Bucket4j token buckets in memory:
 * one instance by design (Document 5, Single-instance rate limits). Each window refills whole at its end, so "5 per
 * minute" means five requests in any one minute window. Time comes from the API's clock.
 */
public class RateLimits {

    /** The endpoint classes of Document 5's limits table that day 4's routes can reach. */
    public enum EndpointClass {
        /** The code exchange: 5 per minute per IP. */
        AUTH("auth"),
        /** Generate, chat message, change, explain: per minute per IP and per hour per sandbox. */
        MODEL("model"),
        /** Deciding a list of cases or a fixture set: 5 per minute per sandbox. */
        BATCH("batch"),
        /** Every other API route: 120 per minute per IP. */
        OTHER("other");

        private final String tag;

        EndpointClass(String tag) {
            this.tag = tag;
        }

        public String tag() {
            return tag;
        }
    }

    public static final long AUTH_PER_MINUTE_PER_IP = 5;
    public static final long BATCH_PER_MINUTE_PER_SANDBOX = 5;
    public static final long OTHER_PER_MINUTE_PER_IP = 120;

    /** Above this many buckets, the full ones (nothing to remember) are dropped on the next request. */
    static final int PURGE_THRESHOLD = 10_000;

    private record Limit(String scope, long capacity, Duration window) {}

    private record Entry(Bucket bucket, long capacity) {}

    private final Map<EndpointClass, List<Limit>> limits;
    private final Map<String, Entry> buckets = new ConcurrentHashMap<>();
    private final TimeMeter time;

    public RateLimits(Clock clock, long modelPerMinutePerIp, long modelPerHourPerSandbox) {
        this.time = new ClockTimeMeter(clock);
        this.limits = Map.of(
                EndpointClass.AUTH, List.of(new Limit("ip", AUTH_PER_MINUTE_PER_IP, Duration.ofMinutes(1))),
                EndpointClass.MODEL, List.of(
                        new Limit("ip", modelPerMinutePerIp, Duration.ofMinutes(1)),
                        new Limit("sandbox", modelPerHourPerSandbox, Duration.ofHours(1))),
                EndpointClass.BATCH, List.of(
                        new Limit("sandbox", BATCH_PER_MINUTE_PER_SANDBOX, Duration.ofMinutes(1))),
                EndpointClass.OTHER, List.of(new Limit("ip", OTHER_PER_MINUTE_PER_IP, Duration.ofMinutes(1))));
    }

    /**
     * Takes one request from every bucket of the class, or none: empty when the request may pass, else how long the
     * client must wait. A per-sandbox limit applies only to an authenticated request.
     */
    public Optional<Duration> tryConsume(EndpointClass endpointClass, String ip, UUID sandboxId) {
        if (buckets.size() > PURGE_THRESHOLD) {
            purge();
        }
        List<Bucket> applicable = endpointClass == null ? List.of() : limits.get(endpointClass).stream()
                .filter(limit -> !limit.scope().equals("sandbox") || sandboxId != null)
                .map(limit -> bucket(endpointClass, limit, limit.scope().equals("ip") ? ip : sandboxId.toString()))
                .toList();
        long waitNanos = 0;
        for (Bucket bucket : applicable) {
            EstimationProbe probe = bucket.estimateAbilityToConsume(1);
            waitNanos = Math.max(waitNanos, probe.canBeConsumed() ? 0 : probe.getNanosToWaitForRefill());
        }
        if (waitNanos > 0) {
            return Optional.of(Duration.ofNanos(waitNanos));
        }
        for (Bucket bucket : applicable) {
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            if (!probe.isConsumed()) {
                return Optional.of(Duration.ofNanos(probe.getNanosToWaitForRefill()));
            }
        }
        return Optional.empty();
    }

    int trackedBuckets() {
        return buckets.size();
    }

    private Bucket bucket(EndpointClass endpointClass, Limit limit, String key) {
        String id = endpointClass.tag() + ":" + limit.scope() + ":" + key;
        return buckets.computeIfAbsent(id, ignored -> new Entry(Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(limit.capacity())
                        .refillIntervally(limit.capacity(), limit.window())
                        .build())
                .withCustomTimePrecision(time)
                .build(), limit.capacity())).bucket();
    }

    private void purge() {
        buckets.values().removeIf(entry -> entry.bucket().getAvailableTokens() >= entry.capacity());
    }

    /** Bucket4j reads the API's clock, so tests move time instead of waiting for it. */
    private record ClockTimeMeter(Clock clock) implements TimeMeter {

        @Override
        public long currentTimeNanos() {
            Instant now = clock.instant();
            return now.getEpochSecond() * 1_000_000_000L + now.getNano();
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }
    }
}
