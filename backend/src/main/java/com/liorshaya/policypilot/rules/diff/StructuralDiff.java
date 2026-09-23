package com.liorshaya.policypilot.rules.diff;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The structural diff of two versions of a rule set (Document 3, Structural diff): fields by {@code name}, rules by
 * {@code id}, the defaults as a whole. A modified rule lists its changed attributes, and its condition the leaves that
 * changed, by JSON pointer; where the two conditions part in shape the change is the whole subtree at that pointer.
 * Added and removed items are carried whole and a modified one with both its sides, so a screen renders the diff alone.
 *
 * <p>Both versions are written by the DSL's writer before they are compared, so the diff, its order and its paths are
 * the same whatever order a store gave a version's keys back in.
 *
 * @param defaults both sides of the defaults, or null when they are the same
 */
public record StructuralDiff(Section fields, Section rules, @Nullable Replaced defaults) {

    private static final RuleSetMapper DSL = new RuleSetMapper();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /**
     * One kind of item: the added and removed ones whole, in the order of the version they are in, and the modified
     * ones in the order of the later version.
     */
    public record Section(List<JsonNode> added, List<JsonNode> removed, List<Modified> modified) {

        public Section {
            added = List.copyOf(added);
            removed = List.copyOf(removed);
            modified = List.copyOf(modified);
        }

        boolean isEmpty() {
            return added.isEmpty() && removed.isEmpty() && modified.isEmpty();
        }
    }

    /** An item on both sides that differs: its name or id, both sides whole, and what changed, in the DSL's order. */
    public record Modified(String key, JsonNode from, JsonNode to, List<Change> changes) {

        public Modified {
            changes = List.copyOf(changes);
        }
    }

    /**
     * One change of a modified item.
     *
     * @param path a JSON pointer into the item, such as {@code /condition/value}
     * @param from the value before, or null when the item had none
     * @param to the value after, or null when the item has none
     */
    public record Change(String path, @Nullable JsonNode from, @Nullable JsonNode to) {}

    /** Both sides of something compared as a whole. */
    public record Replaced(JsonNode from, JsonNode to) {}

    public static StructuralDiff between(RuleSet from, RuleSet to) {
        ObjectNode before = DSL.toJson(from);
        ObjectNode after = DSL.toJson(to);
        JsonNode defaultsBefore = before.required("defaults");
        JsonNode defaultsAfter = after.required("defaults");
        return new StructuralDiff(
                section(before.required("fields"), after.required("fields"), "name"),
                section(before.required("rules"), after.required("rules"), "id"),
                defaultsBefore.equals(defaultsAfter) ? null : new Replaced(defaultsBefore, defaultsAfter));
    }

    /** Whether the two versions have the same fields, rules and defaults. */
    public boolean isEmpty() {
        return fields.isEmpty() && rules.isEmpty() && defaults == null;
    }

    /** The diff as the audit entry stores it and the route answers it (Document 3: never recomputed later). */
    public ObjectNode toJson() {
        ObjectNode json = JSON.createObjectNode();
        json.set("fields", section(fields, "name"));
        json.set("rules", section(rules, "id"));
        if (defaults == null) {
            json.putNull("defaults");
        } else {
            json.putObject("defaults").setAll(sides(defaults.from(), defaults.to()));
        }
        return json;
    }

    private static Section section(JsonNode before, JsonNode after, String key) {
        Map<String, JsonNode> earlier = byKey(before, key);
        Map<String, JsonNode> later = byKey(after, key);
        List<JsonNode> added = new ArrayList<>();
        List<Modified> modified = new ArrayList<>();
        later.forEach((name, item) -> {
            JsonNode was = earlier.get(name);
            if (was == null) {
                added.add(item);
            } else if (!was.equals(item)) {
                modified.add(new Modified(name, was, item, changes(was, item)));
            }
        });
        List<JsonNode> removed = earlier.entrySet().stream().filter(entry -> !later.containsKey(entry.getKey()))
                .map(Map.Entry::getValue).toList();
        return new Section(added, removed, modified);
    }

    private static Map<String, JsonNode> byKey(JsonNode items, String key) {
        Map<String, JsonNode> byKey = new LinkedHashMap<>();
        items.forEach(item -> byKey.put(item.required(key).asString(), item));
        return byKey;
    }

    /** The attributes of an item that changed, in the DSL's order; a condition by its leaves. */
    private static List<Change> changes(JsonNode from, JsonNode to) {
        Set<String> names = new LinkedHashSet<>(from.propertyNames());
        names.addAll(to.propertyNames());
        List<Change> changes = new ArrayList<>();
        for (String name : names) {
            JsonNode was = from.get(name);
            JsonNode is = to.get(name);
            if (was != null && is != null && name.equals("condition")) {
                leaves("/condition", was, is, changes);
            } else if (was == null || !was.equals(is)) {
                changes.add(new Change("/" + name, was, is));
            }
        }
        return changes;
    }

    /** The leaves of a condition that changed; where the two part in shape, the subtree at that pointer. */
    private static void leaves(String path, JsonNode from, JsonNode to, List<Change> changes) {
        if (from.equals(to)) {
            return;
        }
        if (from.isObject() && to.isObject() && sameNames(from, to)) {
            from.propertyNames().forEach(name -> leaves(path + "/" + name, from.required(name), to.required(name),
                    changes));
        } else if (from.isArray() && to.isArray() && from.size() == to.size()) {
            for (int i = 0; i < from.size(); i++) {
                leaves(path + "/" + i, from.get(i), to.get(i), changes);
            }
        } else {
            changes.add(new Change(path, from, to));
        }
    }

    private static boolean sameNames(JsonNode from, JsonNode to) {
        return Set.copyOf(from.propertyNames()).equals(Set.copyOf(to.propertyNames()));
    }

    private static ObjectNode section(Section section, String key) {
        ObjectNode json = JSON.createObjectNode();
        json.putArray("added").addAll(section.added());
        json.putArray("removed").addAll(section.removed());
        ArrayNode modified = json.putArray("modified");
        for (Modified item : section.modified()) {
            ObjectNode entry = modified.addObject().put(key, item.key());
            entry.setAll(sides(item.from(), item.to()));
            ArrayNode changes = entry.putArray("changes");
            item.changes().forEach(change -> changes.addObject().put("path", change.path())
                    .setAll(sides(change.from(), change.to())));
        }
        return json;
    }

    /** Both sides as {@code from} and {@code to}; Jackson writes a missing side as JSON null. */
    private static ObjectNode sides(@Nullable JsonNode from, @Nullable JsonNode to) {
        ObjectNode sides = JSON.createObjectNode();
        sides.set("from", from);
        sides.set("to", to);
        return sides;
    }
}
