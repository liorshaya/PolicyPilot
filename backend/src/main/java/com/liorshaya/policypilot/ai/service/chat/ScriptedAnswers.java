package com.liorshaya.policypilot.ai.service.chat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

/**
 * The scripted questions whose answers the cache keeps, with the label a kept answer meets (Document 4, Prompt 4:
 * Serving the scripted questions from the cache), from {@code prompts/answer/scripted.yml}. A question is scripted by
 * its exact text; the label holds an answer to what the labeled set expects of it, so the cache never serves an
 * answer the evaluation would fail.
 */
public final class ScriptedAnswers {

    private static final String SCRIPTED = "prompts/answer/scripted.yml";
    /** A cited id that ends with this matches any id that starts with what comes before it. */
    private static final String ANY = "*";

    private final List<Label> labels;

    private ScriptedAnswers(List<Label> labels) {
        this.labels = List.copyOf(labels);
    }

    /** Reads the scripted questions; a malformed file is a startup failure. */
    @SuppressWarnings("unchecked")
    public static ScriptedAnswers load() {
        try (InputStream in = new ClassPathResource(SCRIPTED).getInputStream()) {
            Map<String, Object> yaml = new Yaml().load(in);
            List<Label> labels = new ArrayList<>();
            for (Map<String, Object> question : (List<Map<String, Object>>) yaml.get("questions")) {
                labels.add(new Label((String) question.get("id"), (String) question.get("question"),
                        (List<String>) question.get("cites"), (List<String>) question.get("contains")));
            }
            return new ScriptedAnswers(labels);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + SCRIPTED, e);
        }
    }

    public List<Label> labels() {
        return labels;
    }

    /** The label of the scripted question with exactly this text, if it is one. */
    public Optional<Label> labelOf(String question) {
        return labels.stream().filter(label -> label.question().equals(question)).findFirst();
    }

    /**
     * What an answer to a scripted question must be to be kept.
     *
     * @param id the question's id in the labeled set
     * @param question the question as asked
     * @param cites the ids the answer must cite
     * @param contains the words the answer must say
     */
    public record Label(String id, String question, List<String> cites, List<String> contains) {

        public Label {
            cites = List.copyOf(cites);
            contains = List.copyOf(contains);
        }

        /** Whether an answer with this text and these citations cites every id and says every word. */
        public boolean metBy(String text, List<String> cited) {
            return cites.stream().allMatch(expected -> cited.stream().anyMatch(id -> matches(expected, id)))
                    && contains.stream().allMatch(text::contains);
        }

        private static boolean matches(String expected, String id) {
            return expected.endsWith(ANY) ? id.startsWith(expected.substring(0, expected.length() - ANY.length()))
                    : id.equals(expected);
        }
    }
}
