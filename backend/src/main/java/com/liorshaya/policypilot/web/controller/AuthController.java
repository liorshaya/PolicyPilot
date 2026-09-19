package com.liorshaya.policypilot.web.controller;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ApiException;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.request.AccessCodeRequest;
import com.liorshaya.policypilot.web.security.AccessCodeVerifier;
import com.liorshaya.policypilot.web.security.LoginThrottle;
import com.liorshaya.policypilot.web.security.SessionCookies;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /api/v1/auth/code}: exchanges the access code for the signed session cookie (Document 2, API Surface;
 * Document 5, Code exchange). The only API route that needs no cookie. A visitor who already holds a valid cookie
 * keeps their sandbox.
 */
@RestController
public class AuthController {

    private final AccessCodeVerifier verifier;
    private final LoginThrottle throttle;
    private final SessionCookies cookies;
    private final SecurityEvents events;
    private final Clock clock;

    public AuthController(AccessCodeVerifier verifier, LoginThrottle throttle, SessionCookies cookies,
            SecurityEvents events, Clock clock) {
        this.verifier = verifier;
        this.throttle = throttle;
        this.cookies = cookies;
        this.events = events;
        this.clock = clock;
    }

    @Operation(summary = "Exchange the access code for the session cookie")
    @PostMapping(ApiPaths.AUTH_CODE)
    public ResponseEntity<Void> exchange(@RequestBody AccessCodeRequest body, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        Instant now = clock.instant();
        Optional<Duration> locked = throttle.lockedFor(ip, now);
        if (locked.isPresent()) {
            throw ApiException.rateLimited(locked.get());
        }
        if (!verifier.matches(body.code())) {
            LoginThrottle.Failure failure = throttle.recordFailure(ip, now);
            events.authFailed(ip, failure.failuresInWindow(), failure.lockoutStarted());
            if (failure.lockoutStarted()) {
                events.lockoutStarted(ip, LoginThrottle.LOCKOUT);
            }
            throw new ApiException(ErrorCode.ACCESS_CODE_INVALID);
        }
        throttle.recordSuccess(ip);
        SessionCookies.Verification current = cookies.verify(SessionCookies.read(request), now);
        UUID sandboxId = current.isValid() ? current.session().sandboxId() : UUID.randomUUID();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.issue(sandboxId, now).toString())
                .build();
    }
}
