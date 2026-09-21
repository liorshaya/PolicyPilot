package com.liorshaya.policypilot.common;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Security events (Document 5, Security Logging and Monitoring): each one increments its named counter and writes
 * one structured log line. Client IPs and requested ids are logged as keyed hashes, never raw, and rejected input is
 * logged by its code, never its value.
 */
public class SecurityEvents {

    public static final String AUTH_FAILED = "security.auth.failed";
    public static final String AUTH_LOCKOUT = "security.auth.lockout";
    public static final String SESSION_INVALID = "security.session.invalid";
    public static final String AUTHZ_DENIED = "security.authz.denied";
    public static final String RATELIMIT_HIT = "security.ratelimit.hit";
    public static final String INPUT_REJECTED = "security.input.rejected";
    public static final String PROTECTED_WRITE_ATTEMPT = "security.protected.write_attempt";
    public static final String TOOL_REJECTED = "ai.tool.rejected";
    public static final String OUTPUT_DENYLIST = "security.output.denylist";

    private static final Logger LOG = LoggerFactory.getLogger(SecurityEvents.class);
    /** 16 hex characters: enough to tell clients apart in a log, too short to be a useful digest. */
    private static final int HASH_LENGTH = 16;

    private final MeterRegistry registry;
    private final byte[] hashKey;

    /** {@code hashKey} is the per-deployment salt of Document 5, Redaction. */
    public SecurityEvents(MeterRegistry registry, byte[] hashKey) {
        this.registry = registry;
        this.hashKey = hashKey.clone();
    }

    public void authFailed(String ip, int failures, boolean lockout) {
        registry.counter(AUTH_FAILED).increment();
        LOG.atWarn().setMessage(AUTH_FAILED).addKeyValue("ip", hash(ip)).addKeyValue("attempts", failures)
                .addKeyValue("lockout", lockout).log();
    }

    public void lockoutStarted(String ip, Duration duration) {
        registry.counter(AUTH_LOCKOUT).increment();
        LOG.atWarn().setMessage(AUTH_LOCKOUT).addKeyValue("ip", hash(ip))
                .addKeyValue("durationSeconds", duration.toSeconds()).log();
    }

    public void sessionInvalid(String reason) {
        registry.counter(SESSION_INVALID, "reason", reason).increment();
        LOG.atInfo().setMessage(SESSION_INVALID).addKeyValue("reason", reason).log();
    }

    public void authorizationDenied(String entity, UUID sandboxId, String requestedId) {
        registry.counter(AUTHZ_DENIED, "entity", entity).increment();
        LOG.atWarn().setMessage(AUTHZ_DENIED).addKeyValue("entity", entity).addKeyValue("sandbox", sandboxId)
                .addKeyValue("requestedId", hash(requestedId)).log();
    }

    public void rateLimitHit(String endpointClass, String client) {
        registry.counter(RATELIMIT_HIT, "endpoint", endpointClass).increment();
        LOG.atWarn().setMessage(RATELIMIT_HIT).addKeyValue("endpoint", endpointClass)
                .addKeyValue("client", hash(client)).log();
    }

    public void inputRejected(String endpoint, String code) {
        registry.counter(INPUT_REJECTED, "code", code).increment();
        LOG.atInfo().setMessage(INPUT_REJECTED).addKeyValue("endpoint", endpoint).addKeyValue("code", code).log();
    }

    public void protectedWriteAttempt(String entity, UUID sandboxId) {
        registry.counter(PROTECTED_WRITE_ATTEMPT, "entity", entity).increment();
        LOG.atWarn().setMessage(PROTECTED_WRITE_ATTEMPT).addKeyValue("entity", entity)
                .addKeyValue("sandbox", sandboxId).log();
    }

    /** A chat tool refused a call: its arguments were wrong, the id was not found, or the turn's caps were reached. */
    public void toolRejected(String tool, String reason) {
        registry.counter(TOOL_REJECTED, "tool", tool, "reason", reason).increment();
        LOG.atWarn().setMessage(TOOL_REJECTED).addKeyValue("tool", tool).addKeyValue("reason", reason).log();
    }

    /** The denylist scan stopped a streamed answer; the pattern class is logged, never the text. */
    public void outputDenylisted(String promptVersion, String patternClass) {
        registry.counter(OUTPUT_DENYLIST, "prompt", promptVersion, "pattern", patternClass).increment();
        LOG.atWarn().setMessage(OUTPUT_DENYLIST).addKeyValue("prompt", promptVersion)
                .addKeyValue("pattern", patternClass).log();
    }

    /** The keyed hash under which a client or an id is logged. */
    public String hash(String value) {
        return HexFormat.of().formatHex(Hmac.sha256(hashKey, String.valueOf(value))).substring(0, HASH_LENGTH);
    }
}
