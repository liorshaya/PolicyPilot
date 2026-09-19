package com.liorshaya.policypilot.web.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The code-exchange lockout (Document 5, Brute force): 20 failures from one IP within 15 minutes lock that IP for
 * 15 minutes, and while it is locked every exchange is refused, the correct code included. In memory, which is
 * correct for the single Railway instance (Document 5, Single-instance rate limits).
 */
public class LoginThrottle {

    public static final int MAX_FAILURES = 20;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    public static final Duration LOCKOUT = Duration.ofMinutes(15);

    /** Above this many tracked IPs, entries with nothing left to remember are dropped on the next failure. */
    static final int PURGE_THRESHOLD = 10_000;

    /** What a failure did: how many failures the window now holds, and whether it started a lockout. */
    public record Failure(int failuresInWindow, boolean lockoutStarted) {}

    private static final class Client {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant lockedUntil = Instant.MIN;
    }

    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    /** The time left on the lockout of {@code ip}, if it is locked at {@code now}. */
    public Optional<Duration> lockedFor(String ip, Instant now) {
        Client client = clients.get(ip);
        if (client == null) {
            return Optional.empty();
        }
        synchronized (client) {
            return now.isBefore(client.lockedUntil)
                    ? Optional.of(Duration.between(now, client.lockedUntil))
                    : Optional.empty();
        }
    }

    public Failure recordFailure(String ip, Instant now) {
        if (clients.size() > PURGE_THRESHOLD) {
            purge(now);
        }
        Client client = clients.computeIfAbsent(ip, key -> new Client());
        synchronized (client) {
            forgetOld(client, now);
            client.failures.addLast(now);
            boolean lockoutStarted = client.failures.size() >= MAX_FAILURES;
            int failures = client.failures.size();
            if (lockoutStarted) {
                client.lockedUntil = now.plus(LOCKOUT);
                client.failures.clear();
            }
            return new Failure(failures, lockoutStarted);
        }
    }

    public void recordSuccess(String ip) {
        clients.remove(ip);
    }

    int trackedClients() {
        return clients.size();
    }

    private void purge(Instant now) {
        clients.entrySet().removeIf(entry -> {
            Client client = entry.getValue();
            synchronized (client) {
                forgetOld(client, now);
                return client.failures.isEmpty() && !now.isBefore(client.lockedUntil);
            }
        });
    }

    private static void forgetOld(Client client, Instant now) {
        Instant oldest = now.minus(WINDOW);
        while (!client.failures.isEmpty() && !client.failures.peekFirst().isAfter(oldest)) {
            client.failures.removeFirst();
        }
    }
}
