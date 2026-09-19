package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.support.PdfSamples;
import com.liorshaya.policypilot.web.error.ApiExceptionHandler;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import com.liorshaya.policypilot.web.security.SandboxSession;
import com.liorshaya.policypilot.web.validation.PdfTextExtractor;
import com.liorshaya.policypilot.web.validation.UploadReader;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The refusals of {@code POST /api/v1/policies} without Spring (Document 5, Input Validation and File and path
 * injection; Document 2, error envelope). The controller, the upload reader and the policy service are real; the
 * service has no repository, because every request here is refused before anything is read from or written to the
 * database. The paths that store and read policies are {@code PolicyControllerContractIT}'s.
 */
class PolicyControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        SecurityEvents events = new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8));
        PolicyController controller = new PolicyController(
                new PolicyService(null, events, Clock.fixed(NOW, ZoneOffset.UTC)),
                new UploadReader(new PdfTextExtractor()), events);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(new ErrorResponses(new TraceIds(new SimpleTracer()), json)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(json))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SandboxSession(UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a03"), NOW), null, List.of()));
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anInvalidLanguageIs422AndCounted() throws Exception {
        MockHttpServletResponse response = perform(json("{\"title\":\"t\",\"language\":\"fr\",\"text\":\"x\"}"));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/language");
        assertThat(registry.counter("security.input.rejected", "code", "POLICY_INVALID").count()).isEqualTo(1.0);
    }

    @Test
    void textOverItsLimitsIs422WithTheParagraphPointer() throws Exception {
        MockHttpServletResponse response = perform(json(
                "{\"title\":\"t\",\"language\":\"en\",\"text\":\"one\\n\\n" + "a".repeat(4_001) + "\"}"));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/paragraphs/2");
        assertThat(registry.counter("security.input.rejected", "code", "POLICY_INVALID").count()).isEqualTo(1.0);
    }

    @Test
    void anUploadWithJavaScriptIs422UploadRejected() throws Exception {
        MockHttpServletResponse response = perform(upload(PdfSamples.withJavaScript()));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("UPLOAD_REJECTED");
        assertThat(registry.counter("security.input.rejected", "code", "UPLOAD_REJECTED").count()).isEqualTo(1.0);
    }

    @Test
    void anEmptyUploadHasNoParagraph() throws Exception {
        MockHttpServletResponse response = perform(upload(new byte[0]));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].problem"))
                .isEqualTo("has no paragraph");
    }

    private static RequestBuilder json(String body) {
        return post(ApiPaths.POLICIES).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestBuilder upload(byte[] file) {
        return multipart(ApiPaths.POLICIES)
                .file(new MockMultipartFile("file", "policy.pdf", "application/octet-stream", file))
                .param("title", "Upload")
                .param("language", "en");
    }

    private MockHttpServletResponse perform(RequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }
}
