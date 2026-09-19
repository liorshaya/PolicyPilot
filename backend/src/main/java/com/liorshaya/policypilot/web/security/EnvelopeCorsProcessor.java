package com.liorshaya.policypilot.web.security;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

/**
 * Spring's CORS processor, except that a request from an origin outside the allowlist is refused with the envelope
 * (403 {@code CSRF_REJECTED}) and counted as an origin mismatch (Document 5, CSRF and Security Logging), instead of
 * the processor's plain-text body.
 */
class EnvelopeCorsProcessor extends DefaultCorsProcessor {

    private final ErrorResponses errors;
    private final SecurityEvents events;

    EnvelopeCorsProcessor(ErrorResponses errors, SecurityEvents events) {
        this.errors = errors;
        this.events = events;
    }

    @Override
    protected void rejectRequest(ServerHttpResponse response) throws IOException {
        events.sessionInvalid("origin-mismatch");
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getBody().write(errors.body(ErrorCode.CSRF_REJECTED));
        response.flush();
    }
}
