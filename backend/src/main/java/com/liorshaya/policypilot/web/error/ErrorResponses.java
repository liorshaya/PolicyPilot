package com.liorshaya.policypilot.web.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Builds the envelope for controllers and writes it from servlet filters, which run before Spring MVC and its
 * exception handling (authentication, CSRF and rate limits answer from filters).
 */
@Component
public class ErrorResponses {

    private final TraceIds traceIds;
    private final JsonMapper json;

    public ErrorResponses(TraceIds traceIds, JsonMapper json) {
        this.traceIds = traceIds;
        this.json = json;
    }

    public ResponseEntity<ErrorEnvelope> entity(ErrorCode code, List<ErrorDetail> details) {
        return ResponseEntity.status(code.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorEnvelope.of(code, details, traceIds.current()));
    }

    /** A 429 with {@code Retry-After} in whole seconds, rounded up so a client never retries too early. */
    public ResponseEntity<ErrorEnvelope> rateLimited(Duration retryAfter) {
        return ResponseEntity.status(ErrorCode.RATE_LIMITED.status())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(retryAfter)))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ErrorEnvelope.of(ErrorCode.RATE_LIMITED, List.of(), traceIds.current()));
    }

    public void writeRateLimited(HttpServletResponse response, Duration retryAfter) throws IOException {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds(retryAfter)));
        write(response, ErrorCode.RATE_LIMITED);
    }

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), ErrorEnvelope.of(code, List.of(), traceIds.current()));
    }

    /** The serialized envelope of a code, for writers that are not servlet responses. */
    public byte[] body(ErrorCode code) {
        return json.writeValueAsBytes(ErrorEnvelope.of(code, List.of(), traceIds.current()));
    }

    static long retryAfterSeconds(Duration retryAfter) {
        long seconds = retryAfter.toSeconds();
        return Math.max(1, retryAfter.toNanosPart() > 0 ? seconds + 1 : seconds);
    }
}
