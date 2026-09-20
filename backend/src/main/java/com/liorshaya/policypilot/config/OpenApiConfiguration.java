package com.liorshaya.policypilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The OpenAPI document the API serves at {@code /api/docs} (Document 2: "OpenAPI is generated from the controllers
 * with springdoc, so the interviewers can read the contract without the UI"). The server is the API's own root, so
 * the document is the same wherever it is served and the web app's generated client never carries a host.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI policyPilotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("PolicyPilot API")
                        .version("v1")
                        .description("Policies, rule set versions, decisions and their traces. The model proposes and"
                                + " explains, the rules engine decides, a person approves every policy change."))
                .servers(List.of(new Server().url("/").description("This API")));
    }
}
