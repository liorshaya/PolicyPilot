package com.liorshaya.policypilot.eval;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * The field hints of {@code author/v2} (Document 4, Prompt 1: Author, Field hints): the inputs an application supplies,
 * one line each, {@code - <name> (<type>[, <unit>][: <value>, <value>])}, under the line
 * {@code The application supplies these inputs:}. Inputs only: never a derived field, a threshold or a rule. The
 * evaluation writes them from a labeled policy's expected rule set, as step 1 of the demo does from the seeded one's.
 */
public final class FieldHints {

    static final String HEADER = "The application supplies these inputs:";

    private FieldHints() {}

    /** The hints of a rule set's inputs, in its order of fields. */
    public static String of(JsonNode ruleSet) {
        List<String> lines = new ArrayList<>(List.of(HEADER));
        for (JsonNode field : ruleSet.required("fields")) {
            if (field.path("derived").asBoolean(false)) {
                continue;
            }
            StringBuilder line = new StringBuilder("- ").append(field.required("name").asString()).append(" (")
                    .append(field.required("type").asString());
            if (field.hasNonNull("unit")) {
                line.append(", ").append(field.required("unit").asString());
            }
            if (field.has("values")) {
                List<String> values = new ArrayList<>();
                field.required("values").forEach(value -> values.add(value.asString()));
                line.append(": ").append(String.join(", ", values));
            }
            lines.add(line.append(')').toString());
        }
        return String.join("\n", lines);
    }
}
