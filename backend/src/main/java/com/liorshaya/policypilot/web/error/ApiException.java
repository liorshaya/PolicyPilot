package com.liorshaya.policypilot.web.error;

import java.time.Duration;
import java.util.List;

/** A failure the API answers with its envelope; thrown by controllers and validators, mapped by {@link ApiExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<ErrorDetail> details;
    private final transient Duration retryAfter;

    public ApiException(ErrorCode code) {
        this(code, List.of());
    }

    public ApiException(ErrorCode code, List<ErrorDetail> details) {
        this(code, details, null);
    }

    private ApiException(ErrorCode code, List<ErrorDetail> details, Duration retryAfter) {
        super(code.name(), null, false, false);
        this.code = code;
        this.details = List.copyOf(details);
        this.retryAfter = retryAfter;
    }

    /** 429 {@code RATE_LIMITED} with the {@code Retry-After} the client must wait (Document 2, API Surface). */
    public static ApiException rateLimited(Duration retryAfter) {
        return new ApiException(ErrorCode.RATE_LIMITED, List.of(), retryAfter);
    }

    /** The wait for a 429, or null. */
    public Duration retryAfter() {
        return retryAfter;
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
