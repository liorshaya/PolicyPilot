package com.liorshaya.policypilot.web.error;

import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Component;

/**
 * The trace id of the current request (Document 2, Observability: the same id in every log line, on every response
 * and in the error envelope). Micrometer Tracing opens the trace before any PolicyPilot filter runs.
 */
@Component
public class TraceIds {

    /** Written when no trace is open, which only happens outside a request. */
    static final String NONE = "none";

    private final Tracer tracer;

    public TraceIds(Tracer tracer) {
        this.tracer = tracer;
    }

    public String current() {
        TraceContext context = tracer.currentTraceContext().context();
        return context == null ? NONE : context.traceId();
    }
}
