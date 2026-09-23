package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.rules.diff.StructuralDiff;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * The structural diff of two versions (Document 2, {@code GET .../diff/{b}}; Document 3, Structural diff): fields by
 * name, rules by id and the defaults as a whole, in the JSON an approval's audit entry stores.
 *
 * @param defaults both sides of the defaults, or null when they are the same
 */
public record DiffResponse(
        @JsonProperty(required = true) Fields fields,
        @JsonProperty(required = true) Rules rules,
        @JsonProperty(required = true)
        @Schema(implementation = Object.class,
                description = "Both sides of the defaults, or null when they are the same")
        @Nullable Sides defaults) {

    public static DiffResponse of(StructuralDiff diff) {
        StructuralDiff.Replaced defaults = diff.defaults();
        return new DiffResponse(
                new Fields(diff.fields().added(), diff.fields().removed(), diff.fields().modified().stream()
                        .map(item -> new ModifiedField(item.key(), item.from(), item.to(), changes(item))).toList()),
                new Rules(diff.rules().added(), diff.rules().removed(), diff.rules().modified().stream()
                        .map(item -> new ModifiedRule(item.key(), item.from(), item.to(), changes(item))).toList()),
                defaults == null ? null : new Sides(defaults.from(), defaults.to()));
    }

    /** The fields added and removed, whole, and those modified. */
    public record Fields(
            @JsonProperty(required = true) @ArraySchema(schema = @Schema(implementation = Object.class))
            List<JsonNode> added,
            @JsonProperty(required = true) @ArraySchema(schema = @Schema(implementation = Object.class))
            List<JsonNode> removed,
            @JsonProperty(required = true) List<ModifiedField> modified) {}

    /** The rules added and removed, whole, and those modified. */
    public record Rules(
            @JsonProperty(required = true) @ArraySchema(schema = @Schema(implementation = Object.class))
            List<JsonNode> added,
            @JsonProperty(required = true) @ArraySchema(schema = @Schema(implementation = Object.class))
            List<JsonNode> removed,
            @JsonProperty(required = true) List<ModifiedRule> modified) {}

    /** A field on both sides that differs, by its name. */
    public record ModifiedField(
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) @Schema(implementation = Object.class) JsonNode from,
            @JsonProperty(required = true) @Schema(implementation = Object.class) JsonNode to,
            @JsonProperty(required = true) List<Change> changes) {}

    /** A rule on both sides that differs, by its id. */
    public record ModifiedRule(
            @JsonProperty(required = true) String id,
            @JsonProperty(required = true) @Schema(implementation = Object.class) JsonNode from,
            @JsonProperty(required = true) @Schema(implementation = Object.class) JsonNode to,
            @JsonProperty(required = true) List<Change> changes) {}

    /** One change at a JSON pointer into its field or rule; a side is null where the item has nothing there. */
    public record Change(
            @JsonProperty(required = true) String path,
            @JsonProperty(required = true) @Schema(implementation = Object.class) @Nullable JsonNode from,
            @JsonProperty(required = true) @Schema(implementation = Object.class) @Nullable JsonNode to) {}

    /** Both sides of something compared as a whole. */
    public record Sides(
            @JsonProperty(required = true) @Schema(implementation = Object.class) JsonNode from,
            @JsonProperty(required = true) @Schema(implementation = Object.class) JsonNode to) {}

    private static List<Change> changes(StructuralDiff.Modified item) {
        return item.changes().stream().map(change -> new Change(change.path(), change.from(), change.to())).toList();
    }
}
