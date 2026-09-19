package com.liorshaya.policypilot.web.error;

import java.util.List;

/** A failure the API answers with its envelope; thrown by controllers and validators, mapped by {@link ApiExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final transient List<ErrorDetail> details;

    public ApiException(ErrorCode code) {
        this(code, List.of());
    }

    public ApiException(ErrorCode code, List<ErrorDetail> details) {
        super(code.name(), null, false, false);
        this.code = code;
        this.details = List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorDetail> details() {
        return details;
    }
}
