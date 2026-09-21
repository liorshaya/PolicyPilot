package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.OfflineEmbeddings;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

/** The only endpoint of day 1: {@code /actuator/health}, the Railway health check (Document 2, Deployment Topology). */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "spring.ai.openai.api-key=test-key-not-real")
@Import(OfflineEmbeddings.class)
@ActiveProfiles("openai")
class ActuatorHealthIT extends PostgresContainerSupport {

    @Value("${local.server.port}")
    private int port;

    @Test
    void healthEndpointReportsUp() {
        ResponseEntity<String> response = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        String status = JsonPath.read(response.getBody(), "$.status");
        assertThat(status).isEqualTo("UP");
    }

    // Document 2, Observability: a trace id on every response; Micrometer Tracing with Brave writes 16 or 32 hex digits
    @Test
    void everyResponseCarriesATraceId() {
        ResponseEntity<String> response = RestClient.create("http://localhost:" + port)
                .get().uri("/actuator/health")
                .retrieve()
                .toEntity(String.class);

        assertThat(response.getHeaders().getFirst("X-Trace-Id")).matches("[0-9a-f]{16}|[0-9a-f]{32}");
    }
}
