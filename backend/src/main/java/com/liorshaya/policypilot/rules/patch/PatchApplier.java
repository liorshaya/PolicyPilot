package com.liorshaya.policypilot.rules.patch;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Applies a Patches object to a copy of a version, op by op (Document 3, Change Patches): {@code replace} keeps the
 * rule's place, {@code add} appends the rule, {@code remove} drops it and retires its id, {@code add_field} appends
 * the field and {@code set_defaults} replaces the defaults, as the reference implementation applies them. A patch
 * that breaks a constraint of the op table is reported and not applied.
 */
final class PatchApplier {

    private PatchApplier() {}

    /**
     * The copy with the patches applied, the problems found, the patch that wrote each rule and each field, and the
     * ids the patches retired.
     */
    record Applied(ObjectNode document, List<PatchProblem> problems, Map<String, Integer> ruleAt,
            Map<String, Integer> fieldAt, List<String> retired) {

        Applied {
            problems = List.copyOf(problems);
            retired = List.copyOf(retired);
        }
    }

    /**
     * @param patches a Patches object that passed the schema
     * @param base the document of the version the patches apply to; it is not changed
     * @param retiredIds the rule ids the lineage has retired, which an added rule may not take
     */
    static Applied apply(JsonNode patches, ObjectNode base, Collection<String> retiredIds) {
        Run run = new Run(base.deepCopy(), retiredIds);
        JsonNode list = patches.path("patches");
        for (int i = 0; i < list.size(); i++) {
            run.apply(list.get(i), i);
        }
        return new Applied(run.document, run.problems, run.ruleAt, run.fieldAt, run.retired);
    }

    private static final class Run {

        private final ObjectNode document;
        private final ArrayNode rules;
        private final ArrayNode fields;
        private final Set<String> lineageRetired;
        private final List<PatchProblem> problems = new ArrayList<>();
        private final Map<String, Integer> ruleAt = new LinkedHashMap<>();
        private final Map<String, Integer> fieldAt = new LinkedHashMap<>();
        private final List<String> retired = new ArrayList<>();
        /** Every rule id a patch has named so far: a second patch on one of them is repeated. */
        private final Set<String> touched = new HashSet<>();

        Run(ObjectNode document, Collection<String> lineageRetired) {
            this.document = document;
            this.rules = (ArrayNode) document.required("rules");
            this.fields = (ArrayNode) document.required("fields");
            this.lineageRetired = Set.copyOf(lineageRetired);
        }

        void apply(JsonNode patch, int index) {
            String at = "/patches/" + index;
            String ruleId = patch.path("ruleId").asString("");
            switch (PatchOp.of(patch.required("op").asString())) {
                case ADD -> add(patch.required("rule"), ruleId, index, at);
                case REPLACE -> replace(patch.required("rule"), ruleId, index, at);
                case REMOVE -> remove(ruleId, at);
                case ADD_FIELD -> addField(patch.required("field"), index, at);
                case SET_DEFAULTS -> document.set("defaults", patch.required("defaults").deepCopy());
            }
        }

        private void add(JsonNode rule, String ruleId, int index, String at) {
            if (!ruleId.equals(rule.required("id").asString())) {
                idChanged(rule, ruleId, at);
            } else if (indexOf(ruleId) >= 0 || lineageRetired.contains(ruleId) || retired.contains(ruleId)) {
                problems.add(new PatchProblem(PatchCode.PATCH_ID_NOT_NEW, at + "/rule/id", ruleId
                        + " is in use or retired in this rule set; an added rule needs an id of its own",
                        List.of(ruleId), List.of()));
            } else {
                rules.add(rule.deepCopy());
                touched.add(ruleId);
                ruleAt.put(ruleId, index);
            }
        }

        private void replace(JsonNode rule, String ruleId, int index, String at) {
            int position = indexOf(ruleId);
            if (touched.contains(ruleId)) {
                repeated(ruleId, at);
            } else if (position < 0) {
                unknown(ruleId, at);
            } else if (!ruleId.equals(rule.required("id").asString())) {
                idChanged(rule, ruleId, at);
            } else {
                rules.set(position, rule.deepCopy());
                touched.add(ruleId);
                ruleAt.put(ruleId, index);
            }
        }

        private void remove(String ruleId, String at) {
            int position = indexOf(ruleId);
            if (touched.contains(ruleId)) {
                repeated(ruleId, at);
            } else if (position < 0) {
                unknown(ruleId, at);
            } else {
                rules.remove(position);
                touched.add(ruleId);
                retired.add(ruleId);
            }
        }

        private void addField(JsonNode field, int index, String at) {
            String name = field.required("name").asString();
            // the schema already refuses a derived field that is required, so a required field here is a case field
            if (field.path("required").asBoolean(false)) {
                problems.add(new PatchProblem(PatchCode.PATCH_FIELD_REQUIRED, at + "/field/required", name
                        + " is a required case field; an added case field must be optional, or no stored case"
                        + " would still be valid", List.of(), List.of(name)));
            } else {
                fields.add(field.deepCopy());
                fieldAt.put(name, index);
            }
        }

        private void idChanged(JsonNode rule, String ruleId, String at) {
            problems.add(new PatchProblem(PatchCode.PATCH_ID_CHANGED, at + "/rule/id", "the rule of this patch is "
                    + rule.required("id").asString() + ", not " + ruleId, List.of(ruleId), List.of()));
        }

        private void repeated(String ruleId, String at) {
            problems.add(new PatchProblem(PatchCode.PATCH_TARGET_REPEATED, at + "/ruleId",
                    ruleId + " is named by an earlier patch; each rule may be patched once", List.of(ruleId),
                    List.of()));
        }

        private void unknown(String ruleId, String at) {
            problems.add(new PatchProblem(PatchCode.PATCH_TARGET_UNKNOWN, at + "/ruleId",
                    ruleId + " is not a rule of this version", List.of(ruleId), List.of()));
        }

        private int indexOf(String ruleId) {
            for (int i = 0; i < rules.size(); i++) {
                if (ruleId.equals(rules.get(i).required("id").asString())) {
                    return i;
                }
            }
            return -1;
        }
    }
}
