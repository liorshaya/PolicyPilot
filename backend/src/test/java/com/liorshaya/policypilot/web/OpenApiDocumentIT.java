package com.liorshaya.policypilot.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * The OpenAPI document the web app generates its TypeScript client from (Document 2, Frontend Architecture;
 * Document 6, CI stage 1: "the generated client is up to date"). The committed copy is what the client is generated
 * from in CI, so this test is what keeps it equal to the document the API really serves.
 */
class OpenApiDocumentIT extends ApiIntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The copy the frontend generates from, relative to {@code backend/}. */
    static final Path COMMITTED = Path.of("..", "frontend", "src", "api", "openapi.json");
    /** Where this test writes what the API served, so a stale copy is refreshed by one command. */
    static final Path SERVED = Path.of("target", "openapi.json");

    @Test
    void theCommittedDocumentIsTheOneTheApiServes() {
        String served = pretty(api().get("/api/docs").cookie(api().login()).send().body());
        write(SERVED, served);

        assertThat(read(COMMITTED))
                .as("the committed OpenAPI document is stale; refresh it with "
                        + "cp backend/target/openapi.json frontend/src/api/openapi.json "
                        + "and run npm run generate:api in frontend/")
                .isEqualTo(served);
    }

    private static String pretty(String json) {
        return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(JSON.readTree(json)) + "\n";
    }

    private static String read(Path path) {
        try {
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
