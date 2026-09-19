package com.liorshaya.policypilot.web.error;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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

    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), ErrorEnvelope.of(code, List.of(), traceIds.current()));
    }
}
