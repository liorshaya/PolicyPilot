package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.rules.model.Language;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * The fixed not-covered sentence per language (Document 4, Retrieval Pipeline, Threshold, and Citation marker
 * protocol: "stored in prompts/answer/not-covered.yml"). The answer prompt of day 9 quotes the same file, so the API
 * can recognize a refusal the model writes as the one it would have written itself.
 */
public final class NotCoveredSentences {

    private static final String FILE = "prompts/answer/not-covered.yml";

    private final Map<Language, String> sentences;

    private NotCoveredSentences(Map<Language, String> sentences) {
        this.sentences = sentences;
    }

    /** Reads the file; a missing language is a startup failure, not a request that finds no sentence. */
    public static NotCoveredSentences load() {
        try (InputStream in = new ClassPathResource(FILE).getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(in);
            Map<Language, String> sentences = new EnumMap<>(Language.class);
            for (Language language : Language.values()) {
                Object sentence = yaml.get(language.json());
                if (!(sentence instanceof String text) || text.isBlank()) {
                    throw new IllegalStateException(FILE + " has no sentence for " + language.json());
                }
                sentences.put(language, text.strip());
            }
            return new NotCoveredSentences(sentences);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + FILE, e);
        }
    }

    public String of(Language language) {
        return sentences.get(language);
    }
}
