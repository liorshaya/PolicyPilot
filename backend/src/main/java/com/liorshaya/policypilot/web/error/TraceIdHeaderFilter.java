package com.liorshaya.policypilot.web.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the trace id on every response as {@value #HEADER}, so a screenshot of an error can be matched to the logs
 * (Document 2, Observability). Runs right after the observation filter that opens the trace.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class TraceIdHeaderFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";

    private final TraceIds traceIds;

    public TraceIdHeaderFilter(TraceIds traceIds) {
        this.traceIds = traceIds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HEADER, traceIds.current());
        chain.doFilter(request, response);
    }
}
