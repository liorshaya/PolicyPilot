package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.liorshaya.policypilot.ai.ProviderDescription;
import com.liorshaya.policypilot.ai.TokenBudget;
import com.liorshaya.policypilot.support.Requirement;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The system routes without Spring (Document 2, API Surface, {@code GET /system/provider} and {@code GET
 * /system/budget}): the controller turns the active profile's description and the day's budget into the documented
 * bodies. The routes through the running API, the cookie filter and the OpenAPI document included, are
 * {@code SystemControllerContractIT} and {@code BudgetRouteIT}.
 */
@Requirement("FR-21")
class SystemControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A day whose budget is spent, so a body that always says it is not cannot pass. */
    private static final TokenBudget SPENT = new TokenBudget() {
        @Override
        public boolean stopped() {
            return true;
        }

        @Override
        public Instant resumesAt() {
            return Instant.parse("2026-09-29T00:00:00Z");
        }
    };

    // A strong and a fast model that differ, so a body that swaps the two roles cannot pass
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SystemController(
                    new ProviderDescription("ollama", "qwen3:14b", "qwen3:8b", "bge-m3", 1024), SPENT))
            .setMessageConverters(new JacksonJsonHttpMessageConverter(JSON))
            .build();

    // Document 2's body: {provider, chatModels {strong, fast}, embeddingModel, embeddingDimension}. Expected: each name
    // where the document puts it, and nothing else in the body
    @Test
    void answersTheActiveProviderAsItsNames() throws Exception {
        MockHttpServletResponse response = mvc.perform(get(ApiPaths.SYSTEM_PROVIDER)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(JSON.readTree(response.getContentAsString())).isEqualTo(JSON.readTree("""
                {"provider": "ollama", "chatModels": {"strong": "qwen3:14b", "fast": "qwen3:8b"},
                 "embeddingModel": "bge-m3", "embeddingDimension": 1024}"""));
    }

    // Document 2 (2026-09-28, Register phase 4): {spent, resumesAt}, the ledger's hard stop and the next midnight UTC.
    // Expected: the spent day's body, its midnight written as Document 2 writes it
    @Test
    @Requirement("NFR-7")
    void answersTheDaysBudgetAsTheLedgerHasIt() throws Exception {
        MockHttpServletResponse response = mvc.perform(get(ApiPaths.SYSTEM_BUDGET)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(JSON.readTree(response.getContentAsString())).isEqualTo(JSON.readTree("""
                {"spent": true, "resumesAt": "2026-09-29T00:00:00Z"}"""));
    }
}
