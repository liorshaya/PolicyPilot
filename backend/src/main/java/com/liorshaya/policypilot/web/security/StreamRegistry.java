package com.liorshaya.policypilot.web.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The SSE connection cap (Document 5, Availability: 3 open per sandbox, 60 s idle timeout, 5 minutes maximum). A
 * stream holds a lease while it is open; the SSE layer of day 7 asks for one before it starts a stream, touches it on
 * every event, closes it at the end, and completes the streams {@link #expire} returns.
 */
public class StreamRegistry {

    public static final Duration IDLE_TIMEOUT = Duration.ofSeconds(60);
    public static final Duration MAX_LIFETIME = Duration.ofMinutes(5);

    private final int maxPerSandbox;
    private final Map<UUID, Set<Lease>> open = new ConcurrentHashMap<>();

    public StreamRegistry(int maxPerSandbox) {
        this.maxPerSandbox = maxPerSandbox;
    }

    /** A lease for a new stream of the sandbox, or empty when it already has the maximum open. */
    public Optional<Lease> open(UUID sandboxId, Instant now) {
        Set<Lease> leases = open.computeIfAbsent(sandboxId, id -> ConcurrentHashMap.newKeySet());
        synchronized (leases) {
            if (leases.size() >= maxPerSandbox) {
                return Optional.empty();
            }
            Lease lease = new Lease(sandboxId, now);
            leases.add(lease);
            return Optional.of(lease);
        }
    }

    /** Closes and returns every lease idle for 60 s or open for 5 minutes at {@code now}. */
    public List<Lease> expire(Instant now) {
        List<Lease> expired = new ArrayList<>();
        for (Set<Lease> leases : open.values()) {
            synchronized (leases) {
                for (Lease lease : List.copyOf(leases)) {
                    if (lease.expiredAt(now)) {
                        leases.remove(lease);
                        expired.add(lease);
                    }
                }
            }
        }
        return expired;
    }

    public int openStreams(UUID sandboxId) {
        Set<Lease> leases = open.get(sandboxId);
        return leases == null ? 0 : leases.size();
    }

    /** One open stream. */
    public final class Lease {

        private final UUID sandboxId;
        private final Instant openedAt;
        private volatile Instant lastActivity;

        private Lease(UUID sandboxId, Instant openedAt) {
            this.sandboxId = sandboxId;
            this.openedAt = openedAt;
            this.lastActivity = openedAt;
        }

        /** Records an event sent on the stream. */
        public void touch(Instant now) {
            lastActivity = now;
        }

        /** Frees the slot; closing twice is harmless. */
        public void close() {
            Set<Lease> leases = open.get(sandboxId);
            if (leases != null) {
                synchronized (leases) {
                    leases.remove(this);
                }
            }
        }

        public UUID sandboxId() {
            return sandboxId;
        }

        boolean expiredAt(Instant now) {
            return !lastActivity.plus(IDLE_TIMEOUT).isAfter(now) || !openedAt.plus(MAX_LIFETIME).isAfter(now);
        }
    }
}
