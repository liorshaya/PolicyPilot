package com.liorshaya.policypilot.web.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The one error shape of the API (Document 2, API Surface): {@code {code, message, details, traceId}}. */
public record ErrorEnvelope(
        @JsonProperty(required = true) String code,
        @JsonProperty(required = true) String message,
        @JsonProperty(required = true) List<ErrorDetail> details,
        @JsonProperty(required = true) String traceId) {

    public ErrorEnvelope {
        details = List.copyOf(details);
    }

    public static ErrorEnvelope of(ErrorCode code, List<ErrorDetail> details, String traceId) {
        return new ErrorEnvelope(code.name(), code.message(), details, traceId);
    }
}
