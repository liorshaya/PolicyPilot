package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.PayloadTooLargeException;
import com.liorshaya.policypilot.web.error.TraceIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * The body limits and the charset rule, class by class (Document 5, limits table: request body 1 MB, uploads 2 MB;
 * Encoding: UTF-8 only). The same behaviour through the running API is {@code RequestLimitsIT}.
 */
class RequestLimitsFilterTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final RequestLimitsFilter filter = new RequestLimitsFilter(
            new ErrorResponses(new TraceIds(new SimpleTracer()), JsonMapper.builder().build()),
            new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8)));

    @Test
    void anUnparseableContentTypeIsA415AndCounted() throws Exception {
        MockHttpServletResponse response = run(request("application/json;;;=", new byte[0]), null);

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(registry.counter("security.input.rejected", "code", "UNSUPPORTED_MEDIA_TYPE").count()).isEqualTo(1.0);
    }

    @Test
    void anUnknownCharsetIsA415() throws Exception {
        assertThat(run(request("application/json; charset=no-such-charset", new byte[0]), null).getStatus())
                .isEqualTo(415);
    }

    // Expected: Document 5, Encoding: the API accepts UTF-8 only
    @Test
    void aCharsetOtherThanUtf8IsA415() throws Exception {
        MockHttpServletResponse response = run(request("application/json; charset=ISO-8859-1", new byte[0]), null);

        assertThat(response.getStatus()).isEqualTo(415);
    }

    @Test
    void anExplicitUtf8CharsetPasses() throws Exception {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        run(request("application/json; charset=utf-8", new byte[0]), passed);

        assertThat(passed.get()).isNotNull();
    }

    @Test
    void aDeclaredLengthOverTheLimitIsRefusedBeforeReading() throws Exception {
        MockHttpServletRequest request =
                request(MediaType.APPLICATION_JSON_VALUE, new byte[(int) RequestLimitsFilter.MAX_BODY_BYTES + 1]);
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        assertThat(run(request, passed).getStatus()).isEqualTo(413);
        assertThat(passed.get()).isNull();
    }

    @Test
    void aRequestWithoutABodyPasses() throws Exception {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        run(new MockHttpServletRequest("GET", "/api/docs"), passed);

        assertThat(passed.get()).isNotNull();
    }

    @Test
    void byteByByteReadingIsCountedToo() throws Exception {
        AtomicReference<ServletRequest> passed = new AtomicReference<>();
        run(request(MediaType.APPLICATION_JSON_VALUE, new byte[4]), passed);
        ServletInputStream stream = passed.get().getInputStream();

        for (int i = 0; i < 4; i++) {
            stream.read();
        }

        assertThat(stream.read()).isEqualTo(-1);
        assertThat(stream.isFinished()).isTrue();
        assertThat(passed.get().getInputStream()).isSameAs(stream);
    }

    @Test
    void readingAtTheEndOfTheBodyCountsNothing() throws Exception {
        RequestLimitsFilter.LimitedInputStream stream = new RequestLimitsFilter.LimitedInputStream(
                request(MediaType.APPLICATION_JSON_VALUE, new byte[4]).getInputStream(), 4);

        assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(4);
        assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(-1);
    }

    @Test
    void readingPastTheLimitFails() {
        RequestLimitsFilter.LimitedInputStream stream = new RequestLimitsFilter.LimitedInputStream(
                request(MediaType.APPLICATION_JSON_VALUE, new byte[5]).getInputStream(), 4);

        assertThatThrownBy(() -> stream.read(new byte[8], 0, 8)).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void readingExactlyTheLimitSucceeds() throws Exception {
        RequestLimitsFilter.LimitedInputStream stream = new RequestLimitsFilter.LimitedInputStream(
                request(MediaType.APPLICATION_JSON_VALUE, new byte[4]).getInputStream(), 4);

        assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(4);
        assertThat(stream.isReady()).isTrue();
    }

    @Test
    void theReadListenerIsPassedThrough() {
        RecordingStream delegate = new RecordingStream();
        ReadListener listener = new NoopListener();

        new RequestLimitsFilter.LimitedInputStream(delegate, 4).setReadListener(listener);

        assertThat(delegate.listener).isSameAs(listener);
    }

    @Test
    void aMultipartBodyMayReachTheUploadLimit() throws Exception {
        MockHttpServletRequest request =
                request("multipart/form-data; boundary=x", new byte[(int) RequestLimitsFilter.MAX_UPLOAD_BYTES]);
        AtomicReference<ServletRequest> passed = new AtomicReference<>();

        run(request, passed);

        assertThat(passed.get()).isNotNull();
    }

    private static MockHttpServletRequest request(String contentType, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/code");
        request.setContentType(contentType);
        request.setContent(body);
        return request;
    }

    private MockHttpServletResponse run(MockHttpServletRequest request, AtomicReference<ServletRequest> passed)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {
            if (passed != null) {
                passed.set(req);
            }
        });
        return response;
    }

    private static final class RecordingStream extends ServletInputStream {

        private ReadListener listener;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public boolean isFinished() {
            return true;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            this.listener = readListener;
        }
    }

    private static final class NoopListener implements ReadListener {

        @Override
        public void onDataAvailable() {}

        @Override
        public void onAllDataRead() {}

        @Override
        public void onError(Throwable t) {}
    }
}
