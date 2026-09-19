package com.liorshaya.policypilot.web.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.PdfSamples;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * The upload and size tests of Document 5 through the running API (Security Test Plan, integration, Injection: "the
 * .exe upload; the 60-page and JavaScript PDFs"; Document 6 matrix, FR-1: oversized and wrong-type files). A refused
 * upload stores nothing and counts in {@code security.input.rejected}.
 */
@Requirement("FR-1")
class PolicyUploadSecurityIT extends ApiIntegrationTest {

    private static final String POLICIES = "/api/v1/policies";

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private JdbcClient jdbc;

    private String session;

    @BeforeEach
    void logIn() {
        session = api().login();
    }

    // Expected: a PE executable starts with "MZ" and carries NUL bytes; the name says .md, the bytes decide
    @Test
    void renamedExeUploadIsRejected() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};

        HttpResponse<String> response = upload("policy.md", exe);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UPLOAD_REJECTED");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/file");
    }

    @Test
    void sixtyPagePdfUploadIsRejected() {
        HttpResponse<String> response = upload("policy.pdf", PdfSamples.pages(60));

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UPLOAD_REJECTED");
    }

    @Test
    void javaScriptPdfUploadIsRejected() {
        HttpResponse<String> response = upload("policy.pdf", PdfSamples.withJavaScript());

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("UPLOAD_REJECTED");
    }

    @Test
    void wrongTypeFileIsRejected() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0x00, 0x00, 0x00, 0x0d};

        assertThat(upload("policy.txt", png).statusCode()).isEqualTo(422);
    }

    // Expected: Document 5, policy text: 40 KB
    @Test
    void oversizedPolicyTextIsRejected() {
        String body = JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "title", "Too long", "language", "en", "text", ("a".repeat(3_000) + "\n\n").repeat(14)));

        HttpResponse<String> response = api().post(POLICIES).web().cookie(session).json(body).send();

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat((String) JsonPath.read(response.body(), "$.code")).isEqualTo("POLICY_INVALID");
        assertThat((String) JsonPath.read(response.body(), "$.details[0].path")).isEqualTo("/text");
    }

    @Test
    void aControlCharacterInPastedTextIsRejectedWithoutEchoingIt() {
        String body = JsonMapper.builder().build().writeValueAsString(java.util.Map.of(
                "title", "Bell", "language", "en", "text", "ring\u0007the bell"));

        HttpResponse<String> response = api().post(POLICIES).web().cookie(session).json(body).send();

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).doesNotContain("ring").doesNotContain("bell");
    }

    @Test
    void aRejectedUploadStoresNothingAndIsCounted() {
        double before = registry.counter("security.input.rejected", "code", "UPLOAD_REJECTED").count();
        UUID sandbox = UUID.fromString(session.substring(0, 36));

        upload("policy.pdf", PdfSamples.withEmbeddedFile());

        assertThat(registry.counter("security.input.rejected", "code", "UPLOAD_REJECTED").count() - before).isEqualTo(1.0);
        assertThat(jdbc.sql("select count(*) from policy_document where sandbox_id = :s")
                .param("s", sandbox).query(Integer.class).single()).isZero();
    }

    private HttpResponse<String> upload(String fileName, byte[] file) {
        return api().post(POLICIES).web().cookie(session).multipart("Upload", "en", fileName, file).send();
    }
}
