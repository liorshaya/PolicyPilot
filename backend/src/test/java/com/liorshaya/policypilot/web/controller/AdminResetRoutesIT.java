package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

/**
 * {@code POST /api/v1/admin/reset} (Document 2, API Surface and Error codes; Document 5, Nightly reset and Security
 * Logging; Work Plan day 15: "the admin route needs the cookie and the header"). The header is
 * {@code X-PolicyPilot-Admin-Code}; the code is the test configuration's.
 */
@Requirement("NFR-3")
class AdminResetRoutesIT extends ApiIntegrationTest {

    private static final String RESET = "/api/v1/admin/reset";
    private static final String HEADER = "X-PolicyPilot-Admin-Code";

    @Value("${policypilot.admin-code}")
    private String adminCode;

    @Autowired
    private MeterRegistry registry;

    private String session;
    private OpenApiContract contract;

    @BeforeEach
    void logIn() {
        session = api().login();
        contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());
    }

    // Document 5: every /api/** route but the code exchange needs the cookie. Expected: 401 SESSION_INVALID even
    // with the right admin code
    @Test
    void withoutTheCookieIs401() {
        HttpResponse<String> response = api().post(RESET).web().header(HEADER, adminCode).send();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SESSION_INVALID");
    }

    // Document 2, Error codes: ADMIN_CODE_INVALID, 403, without the header. Expected: the documented 403 and one
    // security.admin.refused with reason missing
    @Test
    void withoutTheHeaderIs403() {
        double before = refused("missing");

        HttpResponse<String> response = api().post(RESET).web().cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(contract.violations("post", RESET, 403, response.body())).isEmpty();
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("ADMIN_CODE_INVALID");
        assertThat(refused("missing") - before).isEqualTo(1.0);
    }

    // Document 2, Error codes: ADMIN_CODE_INVALID with another value; the access code is not the admin code.
    // Expected: 403 for each, counted with reason wrong, and the message never repeats what was sent
    @Test
    void withAnotherCodeIs403() {
        double before = refused("wrong");

        HttpResponse<String> wrong = api().post(RESET).web().cookie(session).header(HEADER, adminCode + "x").send();
        HttpResponse<String> access = api().post(RESET).web().cookie(session).header(HEADER, "testcode").send();

        assertThat(wrong.statusCode()).isEqualTo(403);
        assertThat(access.statusCode()).isEqualTo(403);
        assertThat((String) JsonPath.read(wrong.body(), "$.code")).isEqualTo("ADMIN_CODE_INVALID");
        assertThat(wrong.body()).doesNotContain(adminCode);
        assertThat(refused("wrong") - before).isEqualTo(2.0);
    }

    // Document 2: with the cookie and the code, the reset runs and returns what it did. Expected: 200 as the
    // OpenAPI document describes it, and the protected rows present, so nothing to re-seed
    @Test
    void withTheCodeResetsAndAnswersTheContract() {
        HttpResponse<String> response = api().post(RESET).web().cookie(session).header(HEADER, adminCode).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("post", RESET, 200, response.body())).isEmpty();
        assertThat((Boolean) JsonPath.read(response.body(), "$.reseeded")).isFalse();
        assertThat((Integer) JsonPath.read(response.body(), "$.sandboxesDeleted")).isNotNegative();
    }

    private double refused(String reason) {
        return registry.counter("security.admin.refused", "reason", reason).count();
    }
}
