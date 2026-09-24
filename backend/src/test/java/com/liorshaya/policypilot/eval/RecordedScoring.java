package com.liorshaya.policypilot.eval;

import com.liorshaya.policypilot.ai.adapter.ProviderSchemaVariant;
import com.liorshaya.policypilot.ai.prompt.DslCheatSheet;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeService;
import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.engine.Decision;
import com.liorshaya.policypilot.engine.Evaluation;
import com.liorshaya.policypilot.engine.RuleEngine;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.Layer;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationCode;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import com.liorshaya.policypilot.support.ChangeRequests;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.RecordedGateway;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The part of the evaluation runner that scores what a live pass already recorded (Document 6: "a report can be
 * regenerated offline and the recordings double as stubs"): the author pass against the labeled rule sets, and
 * the review pass against the seeded defects. No provider, no database, no clock --- everything is read from
 * {@code fixtures/eval/recordings/}, so the same numbers come out on any machine without a key.
 */
final class RecordedScoring {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RuleSetMapper MAPPER = new RuleSetMapper();
    private static final RuleEngine ENGINE = new RuleEngine();
    private static final RuleSetValidator VALIDATOR = new RuleSetValidator();

    private final String provider;

    RecordedScoring(String provider) {
        this.provider = provider;
    }

    record Authoring(int policies) {}

    record Reviewing(int policies) {}

    /** How many labeled change requests there are, and which of them have a recorded answer. */
    record Changing(int requests, List<String> unrecorded) {}

    /**
     * Rule precision, recall, provenance accuracy, case agreement and calibration, over the recorded runs of one
     * version. A policy whose runs include renders with its field hints (Document 4, Field hints) is scored on those
     * alone: the same version also has renders without hints, which the tests replay and the evaluation does not ask.
     */
    Authoring scoreAuthoring(EvalReport report, String version) {
        Recordings recorded = Recordings.of(provider, "author", version);
        if (recorded.isEmpty()) {
            return new Authoring(0);
        }
        report.model(provider, recorded.model());
        // a version asked with field hints was recorded by the pass that asks every labeled policy (LiveAuthorPassIT),
        // so a policy with no hinted answer is one the provider gave none for within the prompt's timeout
        boolean askedOfEvery = recorded.prompts().stream().anyMatch(prompt -> prompt.contains(FieldHints.HEADER));
        List<String> unanswered = new ArrayList<>();
        int matched = 0;
        int generated = 0;
        int expectedTotal = 0;
        int provenanceCorrect = 0;
        int agreeing = 0;
        int casesTotal = 0;
        int policies = 0;
        int runs = 0;
        int schemaValid = 0;
        int valid = 0;
        List<BigDecimal> right = new ArrayList<>();
        List<BigDecimal> wrong = new ArrayList<>();
        for (String slug : Fixtures.evaluationPolicies()) {
            JsonNode label = Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json");
            RuleSet expected = MAPPER.toRuleSet(label);
            List<ObjectNode> cases = casesOf(slug);
            Recordings forPolicy = recorded.about(paragraphsOf(slug));
            Recordings hinted = forPolicy.about(List.of(FieldHints.of(label)));
            if (askedOfEvery) {
                forPolicy = hinted;
            }
            if (forPolicy.isEmpty()) {
                if (askedOfEvery) {
                    // a run with no answer publishes nothing: every expected rule and labeled case counts against it
                    unanswered.add(slug);
                    runs++;
                    expectedTotal += expected.rules().size();
                    casesTotal += Fixtures.json("eval/policies/" + slug + "/cases.json").required("cases").size();
                    report.policyRow(String.format("| %s | no answer | 0 | 0.00 | 0.00 | 0.00 | 0.00 |", slug));
                }
                continue;
            }
            policies++;
            for (JsonNode answer : forPolicy.responses()) {
                runs++;
                // the same normalization the live path applies before the canonical validator (Document 4, day 7)
                JsonNode document = answer.isObject() ? ProviderSchemaVariant.stripNulls(answer.deepCopy()) : answer;
                Optional<Finding> schemaError = schemaErrorOf(document, slug);
                if (schemaError.isPresent()) {
                    // a draft the schema refuses has no rules to match and decides no case: every expected rule
                    // and every labeled case counts against it, as the pipeline would have had nothing to publish
                    expectedTotal += expected.rules().size();
                    casesTotal += Fixtures.json("eval/policies/" + slug + "/cases.json").required("cases").size();
                    report.policyRow(String.format("| %s | not schema-valid | 0 | 0.00 | 0.00 | 0.00 | 0.00 |", slug));
                    report.mismatch(slug + ": the first answer fails the schema, " + schemaError.get().code()
                            + " at " + schemaError.get().path());
                    continue;
                }
                schemaValid++;
                if (!VALIDATOR.validate(document, ValidationContext.AUTHORING, numberedParagraphsOf(slug), Set.of())
                        .hasErrors()) {
                    valid++;
                }
                RuleSet run = MAPPER.toRuleSet(document);
                RuleMatcher.Result result = RuleMatcher.match(expected, run, cases);
                matched += result.matched();
                generated += result.generatedRules();
                expectedTotal += result.expectedRules();
                provenanceCorrect += (int) result.matches().stream()
                        .filter(RuleMatcher.Match::provenanceCorrect).count();
                confidence(run, result, right, wrong);
                Agreement agreement = agreementOf(expected, run, slug);
                agreeing += agreement.agreeing();
                casesTotal += agreement.total();
                report.policyRow(String.format("| %s | %d | %d | %.2f | %.2f | %.2f | %.2f |", slug,
                        result.generatedRules(), result.matched(), result.recall(), result.precision(),
                        result.provenanceAccuracy(), agreement.ratio()));
                result.matches().stream().filter(match -> !match.matched())
                        .forEach(match -> report.mismatch(slug + " " + match.expectedId() + ": " + match.reason()));
            }
        }
        report.score(provider, "Rule precision", Metric.Score.of(matched, generated));
        report.score(provider, "Rule recall", Metric.Score.of(matched, expectedTotal));
        report.score(provider, "Provenance accuracy", Metric.Score.of(provenanceCorrect, matched));
        report.score(provider, "Case agreement", Metric.Score.of(agreeing, casesTotal));
        report.score(provider, "Confidence calibration", calibration(right, wrong));
        report.score(provider, "Schema-valid first try", Metric.Score.of(schemaValid, runs));
        if (!unanswered.isEmpty()) {
            report.note("The live pass asked every labeled policy; " + unanswered.size() + " got no answer within "
                    + "the author prompt's timeout (" + String.join(", ", unanswered) + "), and each counts as a run "
                    + "with no valid draft, no matched rule and no agreeing case.");
        }
        // the live pass asks each render once and records no repair, so only a run valid on its first answer is
        // known to end valid; when every run is, that is the whole count, and otherwise it is a floor
        Metric.Score validAfterRepairs = Metric.Score.of(valid, runs);
        report.score(provider, "Valid after repairs",
                valid == runs ? validAfterRepairs : validAfterRepairs.asLowerBound());
        if (valid < runs) {
            report.note("Valid after repairs counts the runs valid on their first answer: the live pass records no "
                    + "repair, so the " + (runs - valid) + " that were not might still have ended valid within two.");
        }
        return new Authoring(policies);
    }

    /** The first schema finding of a recorded draft (Document 3, Static Validation, the schema layer), if any. */
    private static Optional<Finding> schemaErrorOf(JsonNode document, String slug) {
        if (!document.isObject()) {
            return Optional.of(new Finding(ValidationCode.DSL_SCHEMA, "", "the answer is not a JSON object", List.of(),
                    List.of()));
        }
        ValidationResult result = VALIDATOR.validate(document, ValidationContext.AUTHORING,
                numberedParagraphsOf(slug), Set.of());
        return result.findings().stream().filter(finding -> finding.code().layer() == Layer.SCHEMA).findFirst();
    }

    /** Reviewer recall and the lower bound of precision, over every policy whose review was recorded. */
    Reviewing scoreReviewing(EvalReport report, String version) {
        Recordings recorded = Recordings.of(provider, "review", version);
        if (recorded.isEmpty()) {
            return new Reviewing(0);
        }
        int found = 0;
        int seeded = 0;
        int answering = 0;
        int findings = 0;
        int policies = 0;
        List<String> unanswered = new ArrayList<>();
        for (String slug : Fixtures.evaluationPolicies()) {
            Recordings forPolicy = recorded.about(paragraphsOf(slug));
            JsonNode defects = Fixtures.json("eval/policies/" + slug + "/seeded.findings.json");
            // the review pass asks every labeled policy, so one with no recorded review got none within the prompt's
            // timeout, and is scored as a review that found nothing
            ObjectNode nothingFound = JSON.createObjectNode();
            nothingFound.putArray("findings");
            JsonNode answer = nothingFound;
            if (forPolicy.isEmpty()) {
                unanswered.add(slug);
            } else {
                policies++;
                answer = forPolicy.responses().getFirst();
            }
            ReviewScoring.Result result = ReviewScoring.score(defects, answer.path("findings"));
            found += result.found();
            seeded += result.caught().size();
            answering += result.precisionLowerBound().numerator();
            findings += result.findings();
            result.caught().stream().filter(caught -> !caught.found())
                    .forEach(caught -> report.mismatch(slug + " " + caught.seededId() + ": " + caught.note()));
        }
        report.score(provider, "Reviewer recall", Metric.Score.of(found, seeded));
        if (!unanswered.isEmpty()) {
            report.note("The review pass asked every labeled policy; " + unanswered.size() + " got no review within "
                    + "the review prompt's timeout (" + String.join(", ", unanswered) + "), and each counts as a "
                    + "review that found none of its seeded defects.");
        }
        report.score(provider, "Reviewer precision", Metric.Score.of(answering, findings).asLowerBound());
        report.note("Reviewer precision is the floor Document 4's definition allows a runner to compute: the "
                + "findings answering a seeded defect over all of them. The other half, \"confirmed real on "
                + "inspection\", needs a person, so a floor under the target settles nothing.");
        return new Reviewing(policies);
    }

    /**
     * Change correctness (Document 4) over the labeled requests of fixtures/eval/changes.json, for one version of the
     * change prompt: each recorded request is replayed ({@link #replayChange}) and its final proposal scored; a
     * request with no recording counts as not correct, so the score never claims more than was measured.
     */
    Changing scoreChanges(EvalReport report, String version) {
        List<JsonNode> labeled = ChangeRequests.labeled();
        List<String> unrecorded = new ArrayList<>();
        int correct = 0;
        for (JsonNode request : labeled) {
            String id = request.required("id").asString();
            Optional<Proposal> proposal = replayChange(request, version);
            if (proposal.isEmpty()) {
                unrecorded.add(id);
                continue;
            }
            ChangeScoring.Verdict verdict = ChangeScoring.score(request, proposal.get());
            if (verdict.correct()) {
                correct++;
            } else {
                report.mismatch(id + ": " + verdict.reason());
            }
        }
        report.score(provider, "Change correctness", Metric.Score.of(correct, labeled.size()));
        return new Changing(labeled.size(), unrecorded);
    }

    /**
     * A labeled change request proposed again through the change use case on its base, rendered by one version of the
     * change prompt, with the candidates its recorded prompt showed the model, so the recorded answer and its repairs
     * replay as the live pass received them; empty when that version has no recording of the request.
     */
    Optional<Proposal> replayChange(JsonNode request, String version) {
        String text = request.required("text").asString();
        Recordings asked = Recordings.of(provider, "change", version).about(List.of(text + "\n</change_request>"));
        if (asked.isEmpty()) {
            return Optional.empty();
        }
        List<String> candidates = candidateIds(asked.prompts().getFirst());
        ChangeService replay = new ChangeService(RecordedGateway.replaying(Recordings.ROOT.resolve(provider)),
                new PromptRegistry(PromptRegistry.PROMPTS, Map.of("change", version)), new DslCheatSheet());
        return Optional.of(replay.propose(ChangeRequests.base(request), text,
                new Candidates(candidates, candidates, List.of()), stage -> { }));
    }

    /** The candidate rules a recorded change prompt showed the model: one rule's JSON per line of the section. */
    static List<String> candidateIds(String prompt) {
        int section = prompt.indexOf('\n', prompt.indexOf("<candidate_rules")) + 1;
        return prompt.substring(section, prompt.indexOf("</candidate_rules>", section)).lines()
                .filter(line -> !line.isBlank())
                .map(line -> JSON.readTree(line.replace("&lt;", "<")).required("id").asString()).toList();
    }

    private record Agreement(int agreeing, int total) {

        double ratio() {
            return total == 0 ? 0 : (double) agreeing / total;
        }
    }

    /**
     * Document 4: "Cases where the generated rule set's outcome equals the expected rule set's outcome". The
     * labeled outcome is the expected rule set's, so the comparison is against {@code cases.json}; a generated
     * rule set the engine cannot run on the labeled inputs agrees on none of them, which is the honest score.
     */
    private Agreement agreementOf(RuleSet expected, RuleSet generated, String slug) {
        JsonNode labeled = Fixtures.json("eval/policies/" + slug + "/cases.json").required("cases");
        CompiledRuleSet compiled;
        try {
            compiled = CompiledRuleSet.compile(generated);
        } catch (RuntimeException e) {
            return new Agreement(0, labeled.size());
        }
        int agreeing = 0;
        for (JsonNode labelledCase : labeled) {
            String expectedOutcome = labelledCase.required("expected").required("outcome").asString();
            try {
                ObjectNode input = (ObjectNode) labelledCase.required("input").deepCopy();
                Evaluation evaluation = ENGINE.evaluate(compiled, input);
                if (evaluation instanceof Decision decision && decision.outcome() != null
                        && decision.outcome().json().equals(expectedOutcome)) {
                    agreeing++;
                }
            } catch (RuntimeException e) {
                // a rule set the labeled inputs do not satisfy decides nothing here, and agrees on nothing
            }
        }
        return new Agreement(agreeing, labeled.size());
    }

    /** Document 4: "Mean confidence of wrong rules is lower than of right rules"; reported, with no target. */
    private static void confidence(RuleSet run, RuleMatcher.Result result, List<BigDecimal> right,
            List<BigDecimal> wrong) {
        List<String> matched = result.matches().stream().filter(RuleMatcher.Match::matched)
                .map(RuleMatcher.Match::generatedId).toList();
        for (Rule rule : run.rules()) {
            if (!(rule.provenance() instanceof Provenance.Quoted quoted) || quoted.confidence() == null) {
                continue;
            }
            (matched.contains(rule.id()) ? right : wrong).add(quoted.confidence());
        }
    }

    private static Metric.Score calibration(List<BigDecimal> right, List<BigDecimal> wrong) {
        BigDecimal meanRight = mean(right);
        BigDecimal meanWrong = mean(wrong);
        boolean calibrated = meanWrong.compareTo(meanRight) < 0;
        return Metric.Score.of(calibrated ? 1 : 0, 1)
                .with("mean confidence " + meanRight + " on matched rules, " + meanWrong + " on unmatched");
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.isEmpty() ? BigDecimal.ZERO
                : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_EVEN);
    }

    /** Every paragraph of a policy, which together tell one policy's recordings from another variant's. */
    /** The paragraphs as the author prompt numbers them, which a draft's provenance cites (Document 4, Prompt 1). */
    private static List<String> numberedParagraphsOf(String slug) {
        return Fixtures.paragraphs(Fixtures.evaluationPolicyText(slug));
    }

    private static List<String> paragraphsOf(String slug) {
        try {
            return Files.readString(Fixtures.path(Fixtures.evaluationPolicyText(slug)), StandardCharsets.UTF_8)
                    .lines().map(String::strip).filter(line -> line.length() > 30).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<ObjectNode> casesOf(String slug) {
        List<ObjectNode> inputs = new ArrayList<>();
        Fixtures.json("eval/policies/" + slug + "/cases.json").required("cases")
                .forEach(labelled -> inputs.add((ObjectNode) labelled.required("input")));
        return inputs;
    }
}
