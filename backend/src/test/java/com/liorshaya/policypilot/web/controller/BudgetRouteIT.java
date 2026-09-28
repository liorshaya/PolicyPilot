package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The day's token budget as the web app reads it (Document 2, {@code GET /system/budget}, added 2026-09-28 for Register
 * phase 4; Document 5, Degradation order: "the token budget stop (cached responses and a banner)"). The ledger is the
 * database's, one row a day in UTC; the clock stands at {@link ApiIntegrationTest#START}, 2026-09-24 09:00 UTC, so the
 * day's count starts again at 2026-09-25 00:00 UTC. Isolated: a stopped day would stop every model call of a test
 * running beside it.
 */
@Requirement("NFR-7")
@Isolated
class BudgetRouteIT extends ApiIntegrationTest {

    private static final String BUDGET = "/api/v1/system/budget";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final LocalDate DAY = LocalDate.ofInstant(START, ZoneOffset.UTC);

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void startTheDayAgain() {
        jdbc.sql("update token_ledger set hard_stop = false where day = :day").param("day", DAY).update();
    }

    // Expected: 200 in the body the OpenAPI document declares, not spent, resuming at the next midnight UTC
    @Test
    void aDayTheLedgerHasNotStoppedIsNotSpent() {
        String session = api().login();
        OpenApiContract contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());

        HttpResponse<String> response = api().get(BUDGET).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", BUDGET, 200, response.body())).isEmpty();
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                {"spent": false, "resumesAt": "2026-09-25T00:00:00Z"}"""));
    }

    // Expected: spent once the ledger's hard stop is set for the day, until the same midnight
    @Test
    void aDayTheLedgerStoppedIsSpentUntilMidnightUtc() {
        jdbc.sql("""
                insert into token_ledger (day, tokens_used, hard_stop) values (:day, 0, true)
                on conflict (day) do update set hard_stop = true""").param("day", DAY).update();

        HttpResponse<String> response = api().get(BUDGET).cookie(api().login()).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                {"spent": true, "resumesAt": "2026-09-25T00:00:00Z"}"""));
    }

    // Document 5: every /api/** route but the code exchange needs the session cookie. Expected: 401 SESSION_INVALID
    @Test
    void refusesARequestWithoutTheSessionCookie() {
        HttpResponse<String> response = api().get(BUDGET).send();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SESSION_INVALID");
    }
}
