package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code POST /api/v1/admin/reset} on a deployment with no admin code (Document 2, Error codes: "or on a deployment
 * that has none"): the variable is optional, and its absence must close the route rather than open it.
 */
@TestPropertySource(properties = "policypilot.admin-code=")
@Requirement("NFR-3")
class AdminResetUnconfiguredIT extends ApiIntegrationTest {

    @Autowired
    private MeterRegistry registry;

    // Expected: 403 ADMIN_CODE_INVALID for an empty header value too, counted with reason none-configured
    @Test
    void noCodeConfiguredRefusesEveryone() {
        double before = registry.counter("security.admin.refused", "reason", "none-configured").count();

        HttpResponse<String> response = api().post("/api/v1/admin/reset").web().cookie(api().login())
                .header("X-PolicyPilot-Admin-Code", " ").send();

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("ADMIN_CODE_INVALID");
        assertThat(registry.counter("security.admin.refused", "reason", "none-configured").count() - before)
                .isEqualTo(1.0);
    }
}
