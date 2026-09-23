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

    private final String provider;

    RecordedScoring(String provider) {
        this.provider = provider;
    }

    record Authoring(int policies) {}

    record Reviewing(int policies) {}

    /** How many labeled change requests there are, and which of them have a recorded answer. */
    record Changing(int requests, List<String> unrecorded) {}

    /** Rule precision, recall, provenance accuracy, case agreement and calibration, over the recorded runs. */
    Authoring scoreAuthoring(EvalReport report) {
        Recordings recorded = Recordings.of(provider, "author", "v1");
        if (recorded.isEmpty()) {
            return new Authoring(0);
        }
        report.model(provider, recorded.model());
        int matched = 0;
        int generated = 0;
        int expectedTotal = 0;
        int provenanceCorrect = 0;
        int agreeing = 0;
        int casesTotal = 0;
        int policies = 0;
        List<BigDecimal> right = new ArrayList<>();
        List<BigDecimal> wrong = new ArrayList<>();
        for (String slug : Fixtures.evaluationPolicies()) {
            JsonNode label = Fixtures.json("eval/policies/" + slug + "/expected.ruleset.json");
            RuleSet expected = MAPPER.toRuleSet(label);
            List<ObjectNode> cases = casesOf(slug);
            Recordings forPolicy = recorded.about(paragraphsOf(slug));
            if (forPolicy.isEmpty()) {
                continue;
            }
            policies++;
            for (JsonNode answer : forPolicy.responses()) {
                // the same normalization the live path applies before the canonical validator (Document 4, day 7)
                RuleSet run = MAPPER.toRuleSet(ProviderSchemaVariant.stripNulls(answer.deepCopy()));
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
        return new Authoring(policies);
    }

    /** Reviewer recall and the lower bound of precision, over every policy whose review was recorded. */
    Reviewing scoreReviewing(EvalReport report) {
        Recordings recorded = Recordings.of(provider, "review", "v1");
        if (recorded.isEmpty()) {
            return new Reviewing(0);
        }
        int found = 0;
        int seeded = 0;
        int answering = 0;
        int findings = 0;
        int policies = 0;
        for (String slug : Fixtures.evaluationPolicies()) {
            Recordings forPolicy = recorded.about(paragraphsOf(slug));
            if (forPolicy.isEmpty()) {
                continue;
            }
            policies++;
            JsonNode defects = Fixtures.json("eval/policies/" + slug + "/seeded.findings.json");
            JsonNode answer = forPolicy.responses().getFirst();
            ReviewScoring.Result result = ReviewScoring.score(defects, answer.path("findings"));
            found += result.found();
            seeded += result.caught().size();
            answering += result.precisionLowerBound().numerator();
            findings += result.findings();
            result.caught().stream().filter(caught -> !caught.found())
                    .forEach(caught -> report.mismatch(slug + " " + caught.seededId() + ": " + caught.note()));
        }
        report.score(provider, "Reviewer recall", Metric.Score.of(found, seeded));
        report.score(provider, "Reviewer precision", Metric.Score.of(answering, findings).asLowerBound());
        report.note("Reviewer precision is the floor Document 4's definition allows a runner to compute: the "
                + "findings answering a seeded defect over all of them. The other half, \"confirmed real on "
                + "inspection\", needs a person, so a floor under the target settles nothing.");
        return new Reviewing(policies);
    }

    /**
     * Change correctness (Document 4) over the labeled requests of fixtures/eval/changes.json: each recorded request
     * is proposed again through the change use case on its base, with the candidates its recorded prompt showed the
     * model, so the recorded answers and repairs replay as the live pass received them; a request with no recording
     * counts as not correct, so the score never claims more than was measured.
     */
    Changing scoreChanges(EvalReport report) {
        Recordings recorded = Recordings.of(provider, "change", "v1");
        ChangeService replay = new ChangeService(RecordedGateway.replaying(Recordings.ROOT.resolve(provider)),
                new PromptRegistry(PromptRegistry.PROMPTS, Map.of()), new DslCheatSheet());
        List<JsonNode> labeled = ChangeRequests.labeled();
        List<String> unrecorded = new ArrayList<>();
        int correct = 0;
        for (JsonNode request : labeled) {
            String id = request.required("id").asString();
            String text = request.required("text").asString();
            Recordings asked = recorded.about(List.of(text + "\n</change_request>"));
            if (asked.isEmpty()) {
                unrecorded.add(id);
                continue;
            }
            List<String> candidates = candidateIds(asked.prompts().getFirst());
            Proposal proposal = replay.propose(ChangeRequests.base(request), text,
                    new Candidates(candidates, candidates, List.of()), stage -> { });
            ChangeScoring.Verdict verdict = ChangeScoring.score(request, proposal);
            if (verdict.correct()) {
                correct++;
            } else {
                report.mismatch(id + ": " + verdict.reason());
            }
        }
        report.score(provider, "Change correctness", Metric.Score.of(correct, labeled.size()));
        return new Changing(labeled.size(), unrecorded);
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
