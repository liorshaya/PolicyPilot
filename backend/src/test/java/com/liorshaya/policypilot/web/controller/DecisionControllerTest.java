package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.web.error.ApiExceptionHandler;
import com.liorshaya.policypilot.web.error.ErrorResponses;
import com.liorshaya.policypilot.web.error.TraceIds;
import com.liorshaya.policypilot.web.security.RateLimits;
import com.liorshaya.policypilot.web.security.SandboxSession;
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
 * The refusals of the decision routes that happen before anything is read from the database: the path ids and the
 * shapes of the two request bodies (Document 2, API Surface; Document 5, Ids in paths and Input validation). The
 * controller and the services are real, with no repositories, because none of these requests reaches one; the paths
 * that decide and store are the integration tests' (Document 6, Test doubles policy).
 */
class DecisionControllerTest {

    private static final Instant NOW = Instant.parse("2026-09-24T09:00:00Z");
    private static final UUID SANDBOX = UUID.fromString("5b0e7f6a-0f3e-4c2a-9a55-1f0d3c2b1a03");
    private static final UUID RULESET = UUID.fromString("0f4c1c9e-0000-4000-8000-000000000001");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        JsonMapper json = JsonMapper.builder().build();
        SecurityEvents events = new SecurityEvents(registry, "salt".getBytes(StandardCharsets.UTF_8));
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        DecisionController controller = new DecisionController(
                new RulesetService(null, null, null, null, null, events, null, clock),
                new DecisionService(null, null, events, clock),
                new RateLimits(clock, 20, 60), events);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler(new ErrorResponses(new TraceIds(new SimpleTracer()), json)))
                .setMessageConverters(new StringHttpMessageConverter(StandardCharsets.UTF_8),
                        new JacksonJsonHttpMessageConverter(json))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new SandboxSession(SANDBOX, NOW), null, List.of()));
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    // Document 5, Ids in paths: a path id that is not a UUID. Expected: 400 REQUEST_INVALID
    @Test
    void decisionIdThatIsNotAUuidIs400() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/v1/decisions/not-a-uuid")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo("REQUEST_INVALID");
    }

    // Document 2, decide: exactly one of case, cases and fixtureSet. Expected: 400 naming no value of the request
    @Test
    void aRequestWithBothACaseAndAFixtureSetIs400() throws Exception {
        MockHttpServletResponse response = decide("{\"case\":{\"age\":34},\"fixtureSet\":\"cases-200\"}");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].problem"))
                .isEqualTo("needs exactly one of case, cases and fixtureSet");
    }

    // Document 5, JSON and deserialization: unknown properties are refused. Expected: 400, the name not echoed
    @Test
    void anUnknownPropertyIs400AndIsNotEchoed() throws Exception {
        MockHttpServletResponse response = decide("{\"caseS\":{\"age\":34}}");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).doesNotContain("caseS");
    }

    // Document 5, Availability: 500 cases per request, checked before the service is called. Expected: 400 at /cases
    @Test
    void moreThan500CasesIs400BeforeTheServiceIsCalled() throws Exception {
        StringBuilder body = new StringBuilder("{\"cases\":[");
        for (int i = 0; i <= 500; i++) {
            body.append(i > 0 ? "," : "").append("{\"age\":34}");
        }

        MockHttpServletResponse response = decide(body.append("]}").toString());

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/cases");
    }

    // Document 3, Simulation: a simulation needs a base and the overrides. Expected: 400 REQUEST_INVALID
    @Test
    void aSimulationWithoutOverridesIs400() throws Exception {
        MockHttpServletResponse response = mvc.perform(post(versionPath() + "/simulate")
                .contentType(MediaType.APPLICATION_JSON).content("{\"case\":{\"age\":34}}")).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat((String) JsonPath.read(response.getContentAsString(), "$.details[0].path")).isEqualTo("/overrides");
    }

    // Document 5, JSON: the body is read with the rule set limits. Expected: 400, counted as a rejected input
    @Test
    void aBodyThatIsNotJsonIs400AndCounted() throws Exception {
        MockHttpServletResponse response = decide("{not json");

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(registry.counter("security.input.rejected", "code", "REQUEST_INVALID").count()).isEqualTo(1.0);
    }

    private MockHttpServletResponse decide(String body) throws Exception {
        return mvc.perform(post(versionPath() + "/decide").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private static String versionPath() {
        return "/api/v1/rulesets/" + RULESET + "/versions/1";
    }
}
