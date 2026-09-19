package com.liorshaya.policypilot.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

/**
 * JSON logging with redaction (Document 5, Injection table, log injection: "unit test on the encoder with a \n and
 * an ANSI escape"; Security Logging, Redaction). Each test encodes one event through Spring Boot's own ECS encoder
 * configured as {@code application.yml} configures it, so the line tested is the line Railway receives.
 */
class RedactingJsonMembersCustomizerTest {

    private static final String ACCESS_CODE = "qwertyui";
    private static final String ADMIN_CODE = "adminzxcv";
    private static final String COOKIE_SECRET = "a-cookie-secret-of-more-than-32-bytes";

    private StructuredLogEncoder encoder;
    private Logger logger;

    @BeforeEach
    void setUp() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("policypilot.access-code", ACCESS_CODE)
                .withProperty("policypilot.admin-code", ADMIN_CODE)
                .withProperty("policypilot.cookie-secret", COOKIE_SECRET)
                .withProperty("logging.structured.json.customizer", RedactingJsonMembersCustomizer.class.getName());
        LoggerContext context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putObject(Environment.class.getName(), environment);
        encoder = new StructuredLogEncoder();
        encoder.setFormat("ecs");
        encoder.setContext(context);
        encoder.start();
        logger = context.getLogger("policypilot.test");
    }

    @Test
    void newlineInAMessageStaysInsideOneJsonLine() {
        String line = encode("first line\n{\"forged\":\"entry\"}");

        assertThat(line.strip()).doesNotContain("\n");
        assertThat(line).contains("first line\\n");
    }

    @Test
    void ansiEscapeIsEncodedNotEmitted() {
        String line = encode("[31mred[0m label");

        assertThat(line).doesNotContain("");
        assertThat(line).containsIgnoringCase("\\u001b[31mred");
    }

    @Test
    void accessCodeIsMasked() {
        assertThat(encode("code " + ACCESS_CODE + " was typed")).doesNotContain(ACCESS_CODE).contains("[REDACTED]");
    }

    @Test
    void adminCodeIsMasked() {
        assertThat(encode("admin " + ADMIN_CODE)).doesNotContain(ADMIN_CODE).contains("[REDACTED]");
    }

    @Test
    void cookieSecretIsMasked() {
        assertThat(encode("secret " + COOKIE_SECRET)).doesNotContain(COOKIE_SECRET).contains("[REDACTED]");
    }

    @Test
    void providerKeyPrefixIsMasked() {
        assertThat(encode("key sk-proj-AbCdEf123456789 rejected")).doesNotContain("AbCdEf123456789").contains("[REDACTED]");
    }

    @Test
    void sessionCookieValueIsMasked() {
        String line = encode("Cookie: pp_session=7f1c0e7a-1111-4222-8333-944445555666.1789830000.c2lnbmF0dXJl; other=1");

        assertThat(line).doesNotContain("c2lnbmF0dXJl").doesNotContain("7f1c0e7a").contains("pp_session=[REDACTED]");
    }

    @Test
    void ordinaryTextIsUntouched() {
        assertThat(encode("policy 3 has 9 paragraphs")).contains("policy 3 has 9 paragraphs");
    }

    private String encode(String message) {
        LoggingEvent event = new LoggingEvent("fqcn", logger, Level.INFO, message, null, null);
        return new String(encoder.encode(event), StandardCharsets.UTF_8);
    }
}
