package com.liorshaya.policypilot.web.security;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.controller.ApiPaths;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies {@link RateLimits} per request (Document 2, {@code web.security.RateLimitFilter}): the class comes from the
 * route, the IP from the connection (Railway's proxy address resolved by the forwarded headers), the sandbox from the
 * session. A refusal is 429 {@code RATE_LIMITED} with {@code Retry-After}, counted in {@code security.ratelimit.hit}.
 * Runs after the session filter and before authorization, so anonymous requests are limited too.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    /** The model-calling routes of Document 2's API table, and retrieval, which embeds its question (Document 5). */
    static final RequestMatcher MODEL_ROUTES = modelRoutes();
    static final RequestMatcher AUTH_ROUTE = PathPatternRequestMatcher.withDefaults()
            .matcher(HttpMethod.POST, ApiPaths.AUTH_CODE);
    static final RequestMatcher API_ROUTES = PathPatternRequestMatcher.withDefaults().matcher("/api/**");

    private final RateLimits limits;
    private final ErrorResponses errors;
    private final SecurityEvents events;

    public RateLimitFilter(RateLimits limits, ErrorResponses errors, SecurityEvents events) {
        this.limits = limits;
        this.errors = errors;
        this.events = events;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !API_ROUTES.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        RateLimits.EndpointClass endpointClass = classify(request);
        UUID sandboxId = currentSandbox();
        Optional<Duration> wait = limits.tryConsume(endpointClass, request.getRemoteAddr(), sandboxId);
        if (wait.isPresent()) {
            events.rateLimitHit(endpointClass.tag(), sandboxId != null ? sandboxId.toString() : request.getRemoteAddr());
            errors.writeRateLimited(response, wait.get());
            return;
        }
        chain.doFilter(request, response);
    }

    static RateLimits.EndpointClass classify(HttpServletRequest request) {
        if (AUTH_ROUTE.matches(request)) {
            return RateLimits.EndpointClass.AUTH;
        }
        if (MODEL_ROUTES.matches(request)) {
            return RateLimits.EndpointClass.MODEL;
        }
        return RateLimits.EndpointClass.OTHER;
    }

    private static UUID currentSandbox() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof SandboxSession session
                ? session.sandboxId()
                : null;
    }

    private static RequestMatcher modelRoutes() {
        PathPatternRequestMatcher.Builder paths = PathPatternRequestMatcher.withDefaults();
        return new OrRequestMatcher(
                paths.matcher(HttpMethod.POST, ApiPaths.V1 + "/policies/{id}/rulesets"),
                paths.matcher(HttpMethod.POST, ApiPaths.V1 + "/chat/sessions/{id}/messages"),
                paths.matcher(HttpMethod.POST, ApiPaths.V1 + "/rulesets/{id}/versions/{no}/changes"),
                paths.matcher(HttpMethod.POST, ApiPaths.V1 + "/decisions/{id}/explain"),
                paths.matcher(HttpMethod.POST, ApiPaths.RULESET_VERSION_RETRIEVAL));
    }
}
