package com.liorshaya.policypilot.web.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The error envelope (Document 2, API Surface, error envelope and Error codes) and responses that never echo input
 * (Document 5, Input Validation, Error responses; OWASP A10). Runs the real handler, filter and message converter on
 * a probe controller, without starting Spring.
 */
class ErrorEnvelopeTest {

    private static final String PLANTED = "<img src=x onerror=alert(1)>";

    private final Tracer tracer = new SimpleTracer();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        TraceIds traceIds = new TraceIds(tracer);
        ErrorResponses responses = new ErrorResponses(traceIds, json);
        mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ApiExceptionHandler(responses))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .addFilters(new TraceIdHeaderFilter(traceIds))
                .build();
    }

    @Test
    void validationFailureReturns422WithPointerDetailsAndTraceId() throws Exception {
        Span span = tracer.nextSpan().start();
        MockHttpServletResponse response = performIn(span, get("/probe/invalid"));

        assertThat(response.getStatus()).isEqualTo(422);
        String body = response.getContentAsString();
        assertThat((String) JsonPath.read(body, "$.code")).isEqualTo("POLICY_INVALID");
        assertThat((String) JsonPath.read(body, "$.details[0].path")).isEqualTo("/paragraphs/3");
        assertThat((String) JsonPath.read(body, "$.traceId")).isEqualTo(span.context().traceId());
    }

    @Test
    void malformedJsonReturns400WithoutEchoingTheBody() throws Exception {
        MockHttpServletResponse response = perform(post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"" + PLANTED));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("REQUEST_INVALID");
        assertThat(response.getContentAsString()).doesNotContain("onerror").doesNotContain("img src");
    }

    @Test
    void unknownPropertyIsRejectedWithItsPointerOnly() throws Exception {
        MockHttpServletResponse response = perform(post("/probe/body")
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"a\", \"" + PLANTED + "\": 1}"));

        assertThat(response.getStatus()).isEqualTo(400);
        String body = response.getContentAsString();
        assertThat((String) JsonPath.read(body, "$.details[0].path")).isEmpty();
        assertThat(body).doesNotContain("onerror");
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternals() throws Exception {
        MockHttpServletResponse response = perform(get("/probe/boom"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getContentAsString())
                .doesNotContain("password=hunter2").doesNotContain("IllegalStateException").doesNotContain("com.liorshaya");
    }

    @Test
    void pathIdWithTheWrongFormatReturns400() throws Exception {
        MockHttpServletResponse response = perform(get("/probe/items/" + "not-a-uuid"));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/id");
    }

    @Test
    void unknownRouteReturns404WithTheEnvelope() throws Exception {
        MockHttpServletResponse response = perform(get("/probe/nothing-here"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void wrongMethodReturns404LikeAnUnknownRoute() throws Exception {
        MockHttpServletResponse response = perform(post("/probe/boom"));

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void unsupportedContentTypeReturns415() throws Exception {
        MockHttpServletResponse response = perform(post("/probe/body")
                .contentType(MediaType.APPLICATION_XML).content("<title>a</title>"));

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void missingBodyReturns400() throws Exception {
        MockHttpServletResponse response = perform(post("/probe/body").contentType(MediaType.APPLICATION_JSON));

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((Integer) JsonPath.read(response.getContentAsString(), "$.details.length()")).isZero();
    }

    @Test
    void wrongTypeInsideAnArrayIsPointedAtByIndex() throws Exception {
        MockHttpServletResponse response = perform(post("/probe/list")
                .contentType(MediaType.APPLICATION_JSON).content("{\"items\": [1, {\"a/b~c\": 2}]}"));

        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/items/1");
    }

    // Expected: RFC 6901, section 3: "~" is written "~0" and "/" is written "~1" in a JSON pointer token
    @Test
    void pointerTokensAreEscapedAsRfc6901Says() {
        JacksonException.Reference reference = new JacksonException.Reference(null, "a/b~c");

        assertThat(ApiExceptionHandler.pointer(List.of(reference))).isEqualTo("/a~1b~0c");
    }

    // Expected: the Error codes table of Document 2, API Surface, for statuses the servlet container produces
    @ParameterizedTest(name = "container status {0} is {1}")
    @CsvSource({
        "400, REQUEST_INVALID", "401, SESSION_INVALID", "403, CSRF_REJECTED", "404, NOT_FOUND", "405, NOT_FOUND",
        "413, PAYLOAD_TOO_LARGE", "415, UNSUPPORTED_MEDIA_TYPE", "429, RATE_LIMITED", "500, INTERNAL_ERROR",
        "502, INTERNAL_ERROR"})
    void containerErrorPageUsesTheDocumentedCode(int status, String code) throws Exception {
        MockMvc errorPage = MockMvcBuilders.standaloneSetup(new EnvelopeErrorController(
                new ErrorResponses(new TraceIds(tracer), JsonMapper.builder().build()))).build();

        MockHttpServletResponse response = errorPage.perform(get("/error")
                .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, status)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(ErrorCode.valueOf(code).status().value());
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo(code);
    }

    @Test
    void containerErrorPageWithoutAStatusIsAnInternalError() throws Exception {
        MockMvc errorPage = MockMvcBuilders.standaloneSetup(new EnvelopeErrorController(
                new ErrorResponses(new TraceIds(tracer), JsonMapper.builder().build()))).build();

        MockHttpServletResponse response = errorPage.perform(get("/error")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(500);
    }

    // Expected: the Error codes table of Document 2, API Surface
    @ParameterizedTest(name = "{0} is HTTP {1}")
    @CsvSource({
        "REQUEST_INVALID, 400", "ACCESS_CODE_INVALID, 401", "SESSION_INVALID, 401", "CSRF_REJECTED, 403",
        "NOT_FOUND, 404", "VERSION_STATUS_CONFLICT, 409", "PAYLOAD_TOO_LARGE, 413", "UNSUPPORTED_MEDIA_TYPE, 415", "POLICY_INVALID, 422",
        "UPLOAD_REJECTED, 422", "RULESET_INVALID, 422", "CASE_INVALID, 422", "RATE_LIMITED, 429", "INTERNAL_ERROR, 500",
        "PROVIDER_UNAVAILABLE, 503"})
    void everyErrorCodeMapsToItsDocumentedStatus(String code, int status) {
        assertThat(ErrorCode.valueOf(code).status().value()).isEqualTo(status);
    }

    @Test
    void theEnumHasExactlyTheDocumentedCodes() {
        assertThat(ErrorCode.values()).hasSize(15);
    }

    @Test
    void everyResponseCarriesATraceIdThatTheEnvelopeRepeats() throws Exception {
        Span span = tracer.nextSpan().start();
        MockHttpServletResponse response = performIn(span, get("/probe/boom"));

        assertThat(response.getHeader(TraceIdHeaderFilter.HEADER)).isEqualTo(span.context().traceId());
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.traceId"))
                .isEqualTo(response.getHeader(TraceIdHeaderFilter.HEADER));
    }

    private MockHttpServletResponse perform(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockHttpServletResponse performIn(Span span, RequestBuilder request) throws Exception {
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            return perform(request);
        } finally {
            span.end();
        }
    }

    record Body(String title) {}

    record Items(List<Integer> items) {}

    @RestController
    static class ProbeController {

        @PostMapping("/probe/body")
        String body(@RequestBody Body body) {
            return body.title();
        }

        @PostMapping("/probe/list")
        String list(@RequestBody Items items) {
            return items.toString();
        }

        @GetMapping("/probe/invalid")
        String invalid() {
            throw new ApiException(ErrorCode.POLICY_INVALID, List.of(new ErrorDetail("/paragraphs/3", "is too long")));
        }

        @GetMapping("/probe/boom")
        String boom() {
            throw new IllegalStateException("password=hunter2 at com.liorshaya.policypilot.Secret");
        }

        @GetMapping("/probe/items/{id}")
        String item(@PathVariable UUID id) {
            return id.toString();
        }
    }
}
