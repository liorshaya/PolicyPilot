package com.liorshaya.policypilot.web.security;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Two of the three CSRF defenses of Document 5 (the third is the CORS allowlist): every state-changing request,
 * the code exchange included, carries {@code X-PolicyPilot-Client: web}, which a cross-site form cannot add, and an
 * {@code Origin} header from the allowlist. A refusal is 403 {@code CSRF_REJECTED}.
 */
public class CsrfDefenseFilter extends OncePerRequestFilter {

    public static final String CLIENT_HEADER = "X-PolicyPilot-Client";
    public static final String CLIENT_VALUE = "web";

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> allowedOrigins;
    private final ErrorResponses errors;
    private final SecurityEvents events;

    public CsrfDefenseFilter(List<String> allowedOrigins, ErrorResponses errors, SecurityEvents events) {
        this.allowedOrigins = Set.copyOf(allowedOrigins);
        this.errors = errors;
        this.events = events;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        if (!CLIENT_VALUE.equals(request.getHeader(CLIENT_HEADER))) {
            refuse(response, "missing-header");
            return;
        }
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !allowedOrigins.contains(origin)) {
            refuse(response, "origin-mismatch");
            return;
        }
        chain.doFilter(request, response);
    }

    private void refuse(HttpServletResponse response, String reason) throws IOException {
        events.sessionInvalid(reason);
        errors.write(response, ErrorCode.CSRF_REJECTED);
    }
}
