package com.liorshaya.policypilot.eval;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Document 4: "Reviewer recall --- seeded defects found with the right kind and an overlapping anchor"; "Reviewer
 * precision --- findings that are seeded or confirmed real on inspection / all findings". An anchor overlaps when
 * the finding names at least one of the seeded defect's paragraphs or at least one of its rules: the reviewer may
 * describe a conflict from either end of it, and Document 4 asks for an overlap, not for the same list.
 *
 * <p>Precision as Document 4 defines it needs a person for the "confirmed real on inspection" half, so what this
 * computes is its lower bound --- the seeded findings alone --- and the report says which number it is. Grading a
 * finding as real because the runner could not seed it would be the runner marking its own homework.
 */
final class ReviewScoring {

    private ReviewScoring() {}

    /** One seeded defect and the finding that caught it, if any. */
    record Caught(String seededId, String kind, @Nullable Integer findingIndex, String note) {

        boolean found() {
            return findingIndex != null;
        }
    }

    record Result(List<Caught> caught, int findings, int matchedFindings) {

        int found() {
            return (int) caught.stream().filter(Caught::found).count();
        }

        Metric.Score recall() {
            return Metric.Score.of(found(), caught.size());
        }

        /** The lower bound of Document 4's precision: the findings that answer a seeded defect. */
        Metric.Score precisionLowerBound() {
            return Metric.Score.of(matchedFindings, findings);
        }
    }

    /**
     * @param seeded the policy's {@code seeded.findings.json}
     * @param review the {@code findings} array of a review answer
     */
    static Result score(JsonNode seeded, JsonNode review) {
        List<JsonNode> findings = new ArrayList<>();
        review.forEach(findings::add);
        List<Caught> caught = new ArrayList<>();
        Set<Integer> answered = new LinkedHashSet<>();
        for (JsonNode defect : seeded.required("findings")) {
            String kind = defect.required("kind").asString();
            Integer at = null;
            for (int index = 0; index < findings.size(); index++) {
                if (answered.contains(index)) {
                    continue;
                }
                if (matches(defect, findings.get(index))) {
                    at = index;
                    answered.add(index);
                    break;
                }
            }
            caught.add(new Caught(defect.required("id").asString(), kind, at,
                    at == null ? "no finding of kind " + kind + " on " + anchorsOf(defect) : "found"));
        }
        return new Result(caught, findings.size(), answered.size());
    }

    /** Document 4: "the right kind and an overlapping anchor". */
    private static boolean matches(JsonNode defect, JsonNode finding) {
        if (!defect.required("kind").asString().equals(finding.path("kind").asString(""))) {
            return false;
        }
        return overlaps(defect, finding, "paragraphIndexes") || overlaps(defect, finding, "ruleIds");
    }

    private static boolean overlaps(JsonNode defect, JsonNode finding, String anchor) {
        Set<String> seeded = valuesOf(defect.path(anchor));
        Set<String> found = valuesOf(finding.path(anchor));
        found.retainAll(seeded);
        return !found.isEmpty();
    }

    private static Set<String> valuesOf(JsonNode array) {
        Set<String> values = new LinkedHashSet<>();
        array.forEach(value -> values.add(value.asString()));
        return values;
    }

    private static String anchorsOf(JsonNode defect) {
        return "paragraphs " + valuesOf(defect.path("paragraphIndexes")) + " or rules "
                + valuesOf(defect.path("ruleIds"));
    }
}
