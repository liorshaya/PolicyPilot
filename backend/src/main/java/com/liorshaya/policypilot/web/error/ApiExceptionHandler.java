package com.liorshaya.policypilot.web.error;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Maps every failure inside Spring MVC to the envelope (Document 2, API Surface). No handler copies an exception
 * message into the response: the message is the code's own, and details carry JSON pointers only (Document 5,
 * Error responses; OWASP A10).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final ErrorResponses responses;

    public ApiExceptionHandler(ErrorResponses responses) {
        this.responses = responses;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorEnvelope> api(ApiException exception) {
        return responses.entity(exception.code(), exception.details());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorEnvelope> unreadable(HttpMessageNotReadableException exception) {
        List<ErrorDetail> details = new ArrayList<>();
        if (exception.getCause() instanceof UnrecognizedPropertyException unknown) {
            // The pointer names the object that holds the property, never the caller's property name
            List<JacksonException.Reference> path = unknown.getPath();
            details.add(new ErrorDetail(pointer(path.subList(0, path.size() - 1)), "has an unknown property"));
        } else if (exception.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            details.add(new ErrorDetail(pointer(jackson.getPath()), "cannot be read as the expected JSON"));
        }
        return responses.entity(ErrorCode.REQUEST_INVALID, details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorEnvelope> typeMismatch(MethodArgumentTypeMismatchException exception) {
        return responses.entity(ErrorCode.REQUEST_INVALID,
                List.of(new ErrorDetail("/" + exception.getName(), "has the wrong format")));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MissingServletRequestPartException.class})
    ResponseEntity<ErrorEnvelope> missing(Exception exception) {
        return responses.entity(ErrorCode.REQUEST_INVALID, List.of());
    }

    @ExceptionHandler({
        NoResourceFoundException.class, NoHandlerFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErrorEnvelope> notFound(Exception exception) {
        return responses.entity(ErrorCode.NOT_FOUND, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorEnvelope> mediaType(HttpMediaTypeNotSupportedException exception) {
        return responses.entity(ErrorCode.UNSUPPORTED_MEDIA_TYPE, List.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorEnvelope> tooLarge(MaxUploadSizeExceededException exception) {
        return responses.entity(ErrorCode.PAYLOAD_TOO_LARGE, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorEnvelope> unexpected(Exception exception) {
        LOG.error("unexpected failure", exception);
        return responses.entity(ErrorCode.INTERNAL_ERROR, List.of());
    }

    /** The JSON pointer of a Jackson path: property names and array indexes, {@code ~} and {@code /} escaped. */
    static String pointer(List<JacksonException.Reference> path) {
        StringBuilder pointer = new StringBuilder();
        for (JacksonException.Reference reference : path) {
            String name = reference.getPropertyName();
            String token = name != null ? name : String.valueOf(reference.getIndex());
            pointer.append('/').append(token.replace("~", "~0").replace("/", "~1"));
        }
        return pointer.toString();
    }
}
