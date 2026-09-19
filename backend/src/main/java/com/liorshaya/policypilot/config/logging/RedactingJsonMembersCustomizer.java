package com.liorshaya.policypilot.config.logging;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

/**
 * Redaction in the JSON log encoder (Document 5, Security Logging, Redaction): every string value of a log line is
 * masked where it holds the access code, the admin code, the cookie secret, a provider key or a session cookie value.
 * The JSON encoding itself keeps newlines and escape characters inside one line (Document 5, log injection).
 *
 * <p>Registered through {@code logging.structured.json.customizer}; Spring Boot passes the {@link Environment}.
 */
public class RedactingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    static final String MASK = "[REDACTED]";

    /** OpenAI keys start with {@code sk-}; anything key-shaped after it is masked. */
    private static final Pattern PROVIDER_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");
    /** The session cookie as it appears in a header or a message: name, equals sign, value. */
    private static final Pattern SESSION_COOKIE = Pattern.compile("(pp_session=)[^;\\s\"]+");

    private final List<String> secrets;

    public RedactingJsonMembersCustomizer(Environment environment) {
        List<String> values = new ArrayList<>();
        for (String property : List.of("policypilot.access-code", "policypilot.admin-code", "policypilot.cookie-secret")) {
            String value = environment.getProperty(property);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        values.sort(Comparator.comparingInt(String::length).reversed());
        this.secrets = List.copyOf(values);
    }

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(JsonWriter.ValueProcessor.of(String.class, this::redact));
    }

    String redact(String value) {
        if (value == null) {
            return null;
        }
        String redacted = value;
        for (String secret : secrets) {
            redacted = redacted.replace(secret, MASK);
        }
        redacted = PROVIDER_KEY.matcher(redacted).replaceAll(MASK);
        return SESSION_COOKIE.matcher(redacted).replaceAll("$1" + Matcher.quoteReplacement(MASK));
    }
}
