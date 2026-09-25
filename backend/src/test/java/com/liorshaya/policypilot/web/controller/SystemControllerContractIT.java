package com.liorshaya.policypilot.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.OpenApiContract;
import com.liorshaya.policypilot.support.Requirement;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/system/provider} through the running API on the {@code openai} profile (Brief FR-21; Document 2,
 * API Surface: "Active provider, model names, embedding dimension", shown in the UI header). The expected body is
 * Document 2's, the names of the profiles table's {@code openai} column; the {@code ollama} column is checked on its
 * own profile by {@code OllamaProfileContextIT}.
 */
@Requirement("FR-21")
class SystemControllerContractIT extends ApiIntegrationTest {

    private static final String PROVIDER = "/api/v1/system/provider";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:https://api.openai.com}")
    private String baseUrl;

    // Document 2, API Surface, the body under openai. Expected: 200, the body the OpenAPI document declares, and
    // exactly gpt-5.6-terra (strong), gpt-5.6-luna (fast), text-embedding-3-small and 1536
    @Test
    void answersTheProviderItsModelsAndTheEmbeddingDimension() {
        String session = api().login();
        OpenApiContract contract = new OpenApiContract(api().get("/api/docs").cookie(session).send().body());

        HttpResponse<String> response = api().get(PROVIDER).cookie(session).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(contract.violations("get", PROVIDER, 200, response.body())).isEmpty();
        assertThat(JSON.readTree(response.body())).isEqualTo(JSON.readTree("""
                {"provider": "openai", "chatModels": {"strong": "gpt-5.6-terra", "fast": "gpt-5.6-luna"},
                 "embeddingModel": "text-embedding-3-small", "embeddingDimension": 1536}"""));
    }

    // Document 2, Security and Demo Protections: "the API never returns provider keys, and /system/provider returns
    // names only". Expected: neither the key this context runs with nor the provider's address, nor any address at all
    @Test
    void answersNamesOnlyNeverTheKeyOrTheProviderAddress() {
        HttpResponse<String> response = api().get(PROVIDER).cookie(api().login()).send();

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain(apiKey).doesNotContain(baseUrl).doesNotContain("://");
    }

    // Document 5: every /api/** route but the code exchange needs the session cookie. Expected: 401 SESSION_INVALID
    @Test
    void refusesARequestWithoutTheSessionCookie() {
        HttpResponse<String> response = api().get(PROVIDER).send();

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("SESSION_INVALID");
    }
}
