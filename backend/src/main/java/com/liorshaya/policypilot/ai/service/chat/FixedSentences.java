package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.rules.model.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * The fixed sentence that ends a turn past the tool caps, per language (Document 4, Prompt 4; Document 5, Tool call
 * volume), from {@code prompts/answer/tool-limit.yml}. A missing language is a startup failure.
 */
public final class FixedSentences {

    private static final String TOOL_LIMIT = "prompts/answer/tool-limit.yml";

    private FixedSentences() {}

    public static Map<Language, String> toolLimit() {
        try (InputStream in = new ClassPathResource(TOOL_LIMIT).getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(in);
            Map<Language, String> sentences = new EnumMap<>(Language.class);
            for (Language language : Language.values()) {
                if (!(yaml.get(language.json()) instanceof String sentence) || sentence.isBlank()) {
                    throw new IllegalStateException(TOOL_LIMIT + " has no sentence for " + language.json());
                }
                sentences.put(language, sentence.strip());
            }
            return sentences;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + TOOL_LIMIT, e);
        }
    }
}
