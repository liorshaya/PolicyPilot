package com.liorshaya.policypilot.web.error;

import java.util.List;

/** The one error shape of the API (Document 2, API Surface): {@code {code, message, details, traceId}}. */
public record ErrorEnvelope(String code, String message, List<ErrorDetail> details, String traceId) {

    public ErrorEnvelope {
        details = List.copyOf(details);
    }

    public static ErrorEnvelope of(ErrorCode code, List<ErrorDetail> details, String traceId) {
        return new ErrorEnvelope(code.name(), code.message(), details, traceId);
    }
}
