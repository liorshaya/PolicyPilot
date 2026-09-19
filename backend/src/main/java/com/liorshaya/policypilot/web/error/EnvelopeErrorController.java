package com.liorshaya.policypilot.web.error;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The container's error page, for failures outside Spring MVC (a malformed request line, a rejected multipart):
 * answers with the envelope instead of Spring Boot's default body, which could carry the exception message.
 */
@Hidden
@RestController
public class EnvelopeErrorController implements ErrorController {

    private final ErrorResponses responses;

    public EnvelopeErrorController(ErrorResponses responses) {
        this.responses = responses;
    }

    @RequestMapping("${server.error.path:/error}")
    ResponseEntity<ErrorEnvelope> error(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int value = status instanceof Integer code ? code : HttpStatus.INTERNAL_SERVER_ERROR.value();
        return responses.entity(codeFor(value), List.of());
    }

    static ErrorCode codeFor(int status) {
        return switch (status) {
            case 400 -> ErrorCode.REQUEST_INVALID;
            case 401 -> ErrorCode.SESSION_INVALID;
            case 403 -> ErrorCode.CSRF_REJECTED;
            case 404, 405 -> ErrorCode.NOT_FOUND;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            case 429 -> ErrorCode.RATE_LIMITED;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}
