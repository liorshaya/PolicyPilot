package com.liorshaya.policypilot.web.error;

import org.springframework.http.HttpStatus;

/**
 * The error codes of the envelope and their HTTP status (Document 2, API Surface, Error codes). The message is
 * fixed per code, so a response can never repeat what the caller sent (Document 5, Error responses).
 */
public enum ErrorCode {
    REQUEST_INVALID(HttpStatus.BAD_REQUEST, "The request is malformed."),
    ACCESS_CODE_INVALID(HttpStatus.UNAUTHORIZED, "The access code is not valid."),
    SESSION_INVALID(HttpStatus.UNAUTHORIZED, "A valid session is required; enter the access code."),
    CSRF_REJECTED(HttpStatus.FORBIDDEN, "The request did not come from the PolicyPilot web app."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found."),
    VERSION_STATUS_CONFLICT(HttpStatus.CONFLICT, "The rule set version's status does not allow this."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The request body is too large."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The content type or charset is not supported."),
    POLICY_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "The policy is not valid."),
    UPLOAD_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT, "The uploaded file cannot be used as a policy."),
    RULESET_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "The rule set is not valid."),
    CASE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT, "The case is not valid against the rule set's fields."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests; retry later."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong; quote the trace id."),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "The model provider is unavailable.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
