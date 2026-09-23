package com.liorshaya.policypilot.ai.prompt;

import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * The prompts of Document 4, loaded from the classpath once (Document 2, Prompts as versioned resources). Every
 * prompt is a directory with a {@code prompt.yml} and one pair of templates per version; the active version is
 * the file's own {@code active} key unless a property overrides it, so an evaluation run can compare two versions
 * without a code change.
 *
 * <p>Everything is read at startup: a missing file, an unreadable metadata key or a template whose placeholders
 * the code cannot fill is a startup failure, never a failed request.
 */
@Component
public final class PromptRegistry {

    /** The five prompts of Document 4 and the repair prompt that two of them share. */
    public static final List<String> PROMPTS = List.of("author", "repair", "answer", "review", "explain", "change");

    private static final String ROOT = "prompts/";
    private static final String CONDUCT = ROOT + "_shared/conduct.st";
    /** What the conduct skeleton expects; the registry fills role and task, the caller fills the language. */
    private static final String CONDUCT_PLACEHOLDER = "conduct";

    private final Map<String, PromptDefinition> prompts;

    /** The prompts of the day, with the versions {@code policypilot.ai.prompt-versions} overrides. */
    @Autowired
    public PromptRegistry(PolicyPilotProperties properties) {
        this(PROMPTS, properties.ai().promptVersions());
    }

    /**
     * @param names the prompt directories to load
     * @param activeVersions the versions that override each prompt's own {@code active} key, by prompt name
     */
    public PromptRegistry(List<String> names, Map<String, String> activeVersions) {
        Map<String, PromptDefinition> loaded = new LinkedHashMap<>();
        String conduct = read(CONDUCT);
        for (String name : names) {
            loaded.put(name, load(name, activeVersions.get(name), conduct));
        }
        // a LinkedHashMap kept unmodifiable: Map.copyOf would lose the order the names were given in
        this.prompts = Collections.unmodifiableMap(loaded);
    }

    /** The prompt by name. */
    public PromptDefinition get(String name) {
        PromptDefinition definition = prompts.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("no prompt named " + name + "; loaded: " + prompts.keySet());
        }
        return definition;
    }

    /** The names the registry loaded, in the order it was given them. */
    public List<String> names() {
        return List.copyOf(prompts.keySet());
    }

    private static PromptDefinition load(String name, @Nullable String override, String conductTemplate) {
        Map<String, Object> meta = metadata(ROOT + name + "/prompt.yml");
        String declaredName = text(meta, "name");
        if (!declaredName.equals(name)) {
            throw new IllegalStateException(
                    "prompts/" + name + "/prompt.yml says name: " + declaredName);
        }
        String version = override == null ? text(meta, "active") : override;
        PromptTemplate conduct = new PromptTemplate(conductTemplate);
        String filledConduct = conduct.render(Map.of(
                "role", text(meta, "role"),
                "task", text(meta, "task"),
                "language", "{language}"));
        String systemText = new PromptTemplate(read(ROOT + name + "/" + version + ".system.st"))
                .render(Map.of(CONDUCT_PLACEHOLDER, filledConduct));
        return new PromptDefinition(
                name,
                version,
                new PromptTemplate(systemText),
                new PromptTemplate(read(ROOT + name + "/" + version + ".user.st")),
                readIfPresent(ROOT + name + "/" + version + ".examples.json"),
                schemaOf(meta),
                role(text(meta, "modelRole")),
                temperature(meta),
                number(meta, "maxOutputTokens").intValue(),
                Duration.ofSeconds(number(meta, "timeoutSeconds").longValue()),
                number(meta, "repairs").intValue(),
                cachePolicy(text(meta, "cache")),
                languages(meta));
    }

    /**
     * The temperature the prompt asks for, or null when it says {@code default}: the strong model of the current
     * OpenAI lineup accepts only its own temperature, and a prompt says so rather than having the adapter guess.
     */
    private static @Nullable Double temperature(Map<String, Object> meta) {
        Object declared = meta.get("temperature");
        if ("default".equals(declared)) {
            return null;
        }
        return number(meta, "temperature").doubleValue();
    }

    private static @Nullable String schemaOf(Map<String, Object> meta) {
        String declared = text(meta, "outputSchema");
        return "none".equals(declared) ? null : declared;
    }

    private static ModelRole role(String declared) {
        return switch (declared) {
            case "strong" -> ModelRole.STRONG;
            case "fast" -> ModelRole.FAST;
            default -> throw new IllegalStateException("modelRole must be strong or fast, not " + declared);
        };
    }

    private static PromptDefinition.CachePolicy cachePolicy(String declared) {
        return switch (declared) {
            case "by-input-hash" -> PromptDefinition.CachePolicy.BY_INPUT_HASH;
            case "by-decision" -> PromptDefinition.CachePolicy.BY_DECISION;
            case "scripted-only" -> PromptDefinition.CachePolicy.SCRIPTED_ONLY;
            case "none" -> PromptDefinition.CachePolicy.NONE;
            default -> throw new IllegalStateException("unknown cache policy " + declared);
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> languages(Map<String, Object> meta) {
        Object declared = meta.get("languages");
        if (!(declared instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalStateException("languages must be a non-empty list");
        }
        return List.copyOf((List<String>) values);
    }

    private static String text(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        if (!(value instanceof String declared) || declared.isBlank()) {
            throw new IllegalStateException("prompt.yml needs a non-empty " + key);
        }
        return declared;
    }

    private static Number number(Map<String, Object> meta, String key) {
        Object value = meta.get(key);
        if (!(value instanceof Number declared)) {
            throw new IllegalStateException("prompt.yml needs a number for " + key);
        }
        return declared;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> metadata(String location) {
        Object parsed = new Yaml().load(read(location));
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new IllegalStateException(location + " is not a YAML mapping");
        }
        return (Map<String, Object>) map;
    }

    private static String read(String location) {
        String content = readIfPresent(location);
        if (content == null) {
            throw new IllegalStateException("no prompt resource at " + location);
        }
        return content;
    }

    private static @Nullable String readIfPresent(String location) {
        ClassLoader loader = PromptRegistry.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(location)) {
            return stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + location, e);
        }
    }

    /** The language a rendered prompt is written in, as the templates spell it. */
    public static String languageName(String code) {
        return switch (code.toLowerCase(Locale.ROOT)) {
            case "he" -> "Hebrew";
            case "en" -> "English";
            default -> throw new IllegalArgumentException("unsupported policy language " + code);
        };
    }
}
