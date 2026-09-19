package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ErrorCode;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.PayloadTooLargeException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Body limits and the charset rule before anything reads a body (Document 5, Availability: request body 1 MB,
 * uploads 2 MB; Encoding: UTF-8 only). A declared length over the limit is refused at once; a body without one
 * (chunked) is counted while it is read. Runs before authentication, so an anonymous flood is cut short too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class RequestLimitsFilter extends OncePerRequestFilter {

    public static final long MAX_BODY_BYTES = 1024 * 1024;
    public static final long MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
    /** A multipart request carries the file plus a few small fields and the part headers. */
    public static final long MULTIPART_OVERHEAD_BYTES = 64 * 1024;

    private final ErrorResponses errors;
    private final SecurityEvents events;

    public RequestLimitsFilter(ErrorResponses errors, SecurityEvents events) {
        this.errors = errors;
        this.events = events;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MediaType type;
        try {
            type = request.getContentType() == null ? null : MediaType.parseMediaType(request.getContentType());
        } catch (InvalidMediaTypeException e) {
            refuse(request, response, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        if (type != null && !isUtf8(type)) {
            refuse(request, response, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        long limit = type != null && MediaType.MULTIPART_FORM_DATA.includes(type)
                ? MAX_UPLOAD_BYTES + MULTIPART_OVERHEAD_BYTES
                : MAX_BODY_BYTES;
        if (request.getContentLengthLong() > limit) {
            refuse(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
            return;
        }
        chain.doFilter(new LimitedRequest(request, limit), response);
    }

    /**
     * No charset parameter means UTF-8 for JSON and is left to the multipart parts otherwise; an unknown charset
     * already fails when the content type is parsed.
     */
    static boolean isUtf8(MediaType type) {
        Charset charset = type.getCharset();
        return charset == null || StandardCharsets.UTF_8.equals(charset);
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        events.inputRejected(request.getMethod() + " " + request.getRequestURI(), code.name());
        errors.write(response, code);
    }

    /** A request whose body stream fails once more than {@code limit} bytes were read. */
    static final class LimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                stream = new LimitedInputStream(super.getInputStream(), limit);
            }
            return stream;
        }
    }

    static final class LimitedInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws PayloadTooLargeException {
            read += n;
            if (read > limit) {
                throw new PayloadTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
