package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.liorshaya.policypilot.ai.ProviderDescription;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/**
 * The provider route without Spring (Document 2, API Surface, {@code GET /system/provider}): the controller turns the
 * active profile's description into the documented body. The route through the running API, the cookie filter and the
 * OpenAPI document included, is {@code SystemControllerContractIT}.
 */
@Requirement("FR-21")
class SystemControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // A strong and a fast model that differ, so a body that swaps the two roles cannot pass
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new SystemController(
                    new ProviderDescription("ollama", "qwen3:14b", "qwen3:8b", "bge-m3", 1024)))
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
}
