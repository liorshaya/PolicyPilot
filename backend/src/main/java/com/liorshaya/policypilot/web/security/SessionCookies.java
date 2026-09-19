package com.liorshaya.policypilot.web.security;

import com.liorshaya.policypilot.common.Hmac;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.ResponseCookie;

/**
 * The session cookie {@code pp_session=<sandboxId>.<issuedAt>.<HMAC>} (Document 5, Code exchange and Cookie):
 * HMAC-SHA256 over {@code sandboxId.issuedAt} with the cookie secret, HttpOnly, Secure, SameSite=Lax, Path=/, a
 * 24-hour life, re-issued when older than one hour.
 */
public class SessionCookies {

    public static final String NAME = "pp_session";
    public static final Duration MAX_AGE = Duration.ofHours(24);
    public static final Duration RENEW_AFTER = Duration.ofHours(1);

    /** Why a cookie was refused; the reason is the tag of {@code security.session.invalid}. */
    public enum Failure {
        MISSING("missing"),
        MALFORMED("malformed"),
        BAD_SIGNATURE("bad-signature"),
        EXPIRED("expired"),
        NOT_YET_VALID("not-yet-valid");

        private final String reason;

        Failure(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    /** The outcome of reading a cookie: a session, or the reason there is none. */
    public record Verification(SandboxSession session, Failure failure) {

        static Verification valid(SandboxSession session) {
            return new Verification(session, null);
        }

        static Verification refused(Failure failure) {
            return new Verification(null, failure);
        }

        public boolean isValid() {
            return session != null;
        }
    }

    private static final Base64.Encoder SIGNATURE_ENCODING = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;

    public SessionCookies(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** The cookie value for a sandbox, issued at {@code now} (whole seconds). */
    public String value(UUID sandboxId, Instant now) {
        String payload = sandboxId + "." + now.getEpochSecond();
        return payload + "." + sign(payload);
    }

    /** The {@code Set-Cookie} header for a sandbox, issued at {@code now}. */
    public ResponseCookie issue(UUID sandboxId, Instant now) {
        return ResponseCookie.from(NAME, value(sandboxId, now))
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(MAX_AGE)
                .build();
    }

    /** Parses, then checks the signature, then the age; a cookie is valid from its issue second for 24 hours. */
    public Verification verify(String value, Instant now) {
        if (value == null || value.isEmpty()) {
            return Verification.refused(Failure.MISSING);
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length != 3) {
            return Verification.refused(Failure.MALFORMED);
        }
        UUID sandboxId;
        long issuedAt;
        try {
            sandboxId = UUID.fromString(parts[0]);
            issuedAt = Long.parseLong(parts[1]);
        } catch (IllegalArgumentException e) {
            return Verification.refused(Failure.MALFORMED);
        }
        if (!sandboxId.toString().equals(parts[0]) || !Long.toString(issuedAt).equals(parts[1])) {
            return Verification.refused(Failure.MALFORMED);
        }
        byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.US_ASCII))) {
            return Verification.refused(Failure.BAD_SIGNATURE);
        }
        Instant issued = Instant.ofEpochSecond(issuedAt);
        if (issued.isAfter(now)) {
            return Verification.refused(Failure.NOT_YET_VALID);
        }
        if (!issued.plus(MAX_AGE).isAfter(now)) {
            return Verification.refused(Failure.EXPIRED);
        }
        return Verification.valid(new SandboxSession(sandboxId, issued));
    }

    /** The value of the session cookie on a request, or null; a duplicated cookie counts as its first occurrence. */
    public static String read(HttpServletRequest request) {
        Cookie[] all = request.getCookies();
        if (all == null) {
            return null;
        }
        for (Cookie cookie : all) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** True when the session is old enough to be re-issued with a fresh {@code issuedAt}. */
    public static boolean dueForRenewal(SandboxSession session, Instant now) {
        return !session.issuedAt().plus(RENEW_AFTER).isAfter(now);
    }

    private String sign(String payload) {
        return SIGNATURE_ENCODING.encodeToString(Hmac.sha256(secret, payload));
    }
}
