package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import jakarta.servlet.http.Cookie;

/** Cookie signing and expiry (Document 5, Code exchange and Cookie; Security Test Plan, unit). */
class SessionCookiesTest {

    private static final String SECRET = "a-cookie-secret-of-more-than-32-bytes";
    private static final UUID SANDBOX = UUID.fromString("7f1c0e7a-1111-4222-8333-944445555666");
    private static final Instant ISSUED = Instant.parse("2026-09-24T09:00:00Z");

    private final SessionCookies cookies = new SessionCookies(SECRET);

    // Expected: Document 5, Cookie: HMAC-SHA256 over "sandboxId.issuedAt" with the secret; computed here with the
    // JDK's Mac directly, base64url without padding
    @Test
    void signatureIsTheHmacOfSandboxIdAndIssuedAt() throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String payload = SANDBOX + "." + ISSUED.getEpochSecond();
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

        assertThat(cookies.value(SANDBOX, ISSUED)).isEqualTo(payload + "." + signature);
    }

    // Expected: Document 5, sequence diagram: pp_session=...; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=86400
    @Test
    void issuedCookieHasTheDocumentedAttributes() {
        String header = cookies.issue(SANDBOX, ISSUED).toString();

        assertThat(header).startsWith("pp_session=" + SANDBOX + ".1790240400.")
                .contains("; Path=/", "; Max-Age=86400", "; Secure", "; HttpOnly", "; SameSite=Lax");
    }

    // Expected: 2026-09-24T09:00:00Z is 1790240400 seconds after the epoch (computed with Python's datetime)
    @Test
    void valueIsSandboxIdDotIssuedAtDotSignature() {
        assertThat(cookies.value(SANDBOX, ISSUED))
                .matches("7f1c0e7a-1111-4222-8333-944445555666\\.1790240400\\.[A-Za-z0-9_-]{43}");
    }

    @Test
    void validCookieResolvesItsSandbox() {
        SessionCookies.Verification verification = cookies.verify(cookies.value(SANDBOX, ISSUED), ISSUED);

        assertThat(verification.session()).isEqualTo(new SandboxSession(SANDBOX, ISSUED));
    }

    @Test
    void tamperedSignatureIsRejected() {
        String value = cookies.value(SANDBOX, ISSUED);
        char last = value.charAt(value.length() - 1);
        String tampered = value.substring(0, value.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThat(cookies.verify(tampered, ISSUED).failure()).isEqualTo(SessionCookies.Failure.BAD_SIGNATURE);
    }

    @Test
    void tamperedSandboxIdIsRejected() {
        String value = cookies.value(SANDBOX, ISSUED);
        String otherSandbox = "00000000-1111-4222-8333-944445555666" + value.substring(36);

        assertThat(cookies.verify(otherSandbox, ISSUED).failure()).isEqualTo(SessionCookies.Failure.BAD_SIGNATURE);
    }

    @Test
    void tamperedIssuedAtIsRejected() {
        String value = cookies.value(SANDBOX, ISSUED).replace(".1790240400.", ".1790326800.");

        assertThat(cookies.verify(value, ISSUED.plusSeconds(86_400)).failure())
                .isEqualTo(SessionCookies.Failure.BAD_SIGNATURE);
    }

    @Test
    void cookieSignedWithAnotherSecretIsRejected() {
        String value = new SessionCookies("another-secret-of-more-than-32-bytes").value(SANDBOX, ISSUED);

        assertThat(cookies.verify(value, ISSUED).failure()).isEqualTo(SessionCookies.Failure.BAD_SIGNATURE);
    }

    // Expected: Document 5, Cookie: 24-hour expiry
    @Test
    void cookieIsValidJustBefore24Hours() {
        Instant justBefore = ISSUED.plus(Duration.ofHours(24)).minusSeconds(1);

        assertThat(cookies.verify(cookies.value(SANDBOX, ISSUED), justBefore).isValid()).isTrue();
    }

    @Test
    void cookieIsExpiredAt24Hours() {
        Instant expiry = ISSUED.plus(Duration.ofHours(24));

        assertThat(cookies.verify(cookies.value(SANDBOX, ISSUED), expiry).failure())
                .isEqualTo(SessionCookies.Failure.EXPIRED);
    }

    @Test
    void cookieIssuedInTheFutureIsRejected() {
        assertThat(cookies.verify(cookies.value(SANDBOX, ISSUED), ISSUED.minusSeconds(1)).failure())
                .isEqualTo(SessionCookies.Failure.NOT_YET_VALID);
    }

    @ParameterizedTest(name = "[{0}] is {1}")
    @CsvSource(nullValues = "NULL", value = {
        "NULL, MISSING",
        "'', MISSING",
        "a.b, MALFORMED",
        "a.b.c.d, MALFORMED",
        "not-a-uuid.1790240400.sig, MALFORMED",
        "7f1c0e7a-1111-4222-8333-944445555666.soon.sig, MALFORMED",
        "7F1C0E7A-1111-4222-8333-944445555666.1790240400.sig, MALFORMED",
        "7f1c0e7a-1111-4222-8333-944445555666.+1790240400.sig, MALFORMED",
        "7f1c0e7a-1111-4222-8333-944445555666.1790240400.sig, BAD_SIGNATURE"})
    void malformedValuesAreRejected(String value, SessionCookies.Failure failure) {
        assertThat(cookies.verify(value, ISSUED).failure()).isEqualTo(failure);
    }

    // Expected: Document 5, Cookie: re-issued when older than one hour
    @Test
    void sessionIsDueForRenewalAfterOneHour() {
        SandboxSession session = new SandboxSession(SANDBOX, ISSUED);

        assertThat(SessionCookies.dueForRenewal(session, ISSUED.plus(Duration.ofHours(1)).minusSeconds(1))).isFalse();
        assertThat(SessionCookies.dueForRenewal(session, ISSUED.plus(Duration.ofHours(1)))).isTrue();
    }

    @Test
    void readTakesTheFirstSessionCookieOfTheRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"), new Cookie("pp_session", "first"), new Cookie("pp_session", "second"));

        assertThat(SessionCookies.read(request)).isEqualTo("first");
    }

    @Test
    void readReturnsNullWithoutCookies() {
        assertThat(SessionCookies.read(new MockHttpServletRequest())).isNull();
    }

    @Test
    void readReturnsNullWithoutASessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("other", "x"));

        assertThat(SessionCookies.read(request)).isNull();
    }
}
