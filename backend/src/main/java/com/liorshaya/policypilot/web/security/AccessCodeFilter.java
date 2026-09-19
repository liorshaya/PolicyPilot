package com.liorshaya.policypilot.web.security;

import com.liorshaya.policypilot.common.SecurityEvents;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * The session filter (Document 2, Security and Demo Protections, Access code): a request with a valid signed cookie
 * becomes authenticated as its {@link SandboxSession}, and a cookie older than an hour is re-issued; anything else
 * stays anonymous, so the authorization rules answer 401 on every route but the public ones. Every refused cookie
 * counts in {@code security.session.invalid} with its reason (Document 5, Security Logging).
 */
public class AccessCodeFilter extends OncePerRequestFilter {

    private final SessionCookies cookies;
    private final Clock clock;
    private final SecurityEvents events;
    private final RequestMatcher publicRoutes;

    public AccessCodeFilter(SessionCookies cookies, Clock clock, SecurityEvents events, RequestMatcher publicRoutes) {
        this.cookies = cookies;
        this.clock = clock;
        this.events = events;
        this.publicRoutes = publicRoutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return publicRoutes.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Instant now = clock.instant();
        SessionCookies.Verification verification = cookies.verify(SessionCookies.read(request), now);
        if (verification.isValid()) {
            SandboxSession session = verification.session();
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(session, null, List.of()));
            if (SessionCookies.dueForRenewal(session, now)) {
                response.addHeader(HttpHeaders.SET_COOKIE, cookies.issue(session.sandboxId(), now).toString());
            }
        } else {
            events.sessionInvalid(verification.failure().reason());
        }
        chain.doFilter(request, response);
    }
}
