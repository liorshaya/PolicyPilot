package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.web.error.ApiExceptionHandler;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import com.liorshaya.policypilot.web.security.SandboxSession;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.test.simple.SimpleTracer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The refusals of the rule set routes that happen before anything is read from the database: the path ids of
 * Document 5 and a body that is not a rule set document. The controller and the service are real, and the service
 * has no repositories, because none of these requests reaches one; the paths that read and write rule sets are
 * {@code RulesetControllerContractIT}'s and {@code RuleSetEditIT}'s (Document 6, Test doubles policy).
 */
class RulesetControllerTest {

    private static final UUID SANDBOX = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a03");
    private static final UUID RULESET = UUID.fromString("0f4c1c9e-0000-4000-8000-000000000001");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        SecurityEvents events = new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8));
        RulesetService rulesets = new RulesetService(null, null, null, null, null, events, null, null);
        mvc = MockMvcBuilders.standaloneSetup(new RulesetController(rulesets, null, events))
                .setControllerAdvice(new ApiExceptionHandler(new ErrorResponses(new TraceIds(new SimpleTracer()), json)))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new JacksonJsonHttpMessageConverter(json))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SandboxSession(SANDBOX, Instant.parse("2026-09-24T09:00:00Z")), null, List.of()));
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    // Document 5, Ids in paths: anything else is refused before any lookup. Expected: 400 REQUEST_INVALID
    @Test
    void versionNumberThatIsNotAPositiveIntegerIs400() throws Exception {
        MockHttpServletResponse response = mvc.perform(get(path(0))).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("REQUEST_INVALID");
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/versions");
    }

    // Document 5, Ids in paths: a path id that is not a UUID. Expected: 400 REQUEST_INVALID
    @Test
    void rulesetIdThatIsNotAUuidIs400() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/v1/rulesets/not-a-uuid/versions/1"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    // Document 5, JSON and deserialization: the body is read with the rule set limits. Expected: 400, counted
    @Test
    void aBodyThatIsNotARuleSetDocumentIs400AndCounted() throws Exception {
        MockHttpServletResponse response = mvc.perform(put(path(1) + "/rules")
                .contentType(MediaType.APPLICATION_JSON).content("{not json")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("REQUEST_INVALID");
        assertThat(registry.counter("security.input.rejected", "code", "REQUEST_INVALID").count()).isEqualTo(1.0);
    }

    // Document 5, Ids in paths, and Document 2, review_json: a finding id is F-1, F-2, ... Expected: 400
    // REQUEST_INVALID before any lookup (the service here has no repositories)
    @Test
    void aFindingIdThatIsNotOneTheReviewGivesIs400() throws Exception {
        MockHttpServletResponse response = mvc.perform(post(path(1) + "/findings/R-100/acknowledge")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/findingId");
    }

    // Document 2, acknowledge row: a resolution is one of three names. Expected: 400 at /resolution
    @Test
    void aResolutionThatIsNotOneOfTheThreeIs400() throws Exception {
        MockHttpServletResponse response = mvc.perform(post(path(1) + "/findings/F-1/acknowledge")
                .contentType(MediaType.APPLICATION_JSON).content("{\"resolution\": \"ignored\"}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path"))
                .isEqualTo("/resolution");
    }

    private static String path(int versionNo) {
        return "/api/v1/rulesets/" + RULESET + "/versions/" + versionNo;
    }
}
