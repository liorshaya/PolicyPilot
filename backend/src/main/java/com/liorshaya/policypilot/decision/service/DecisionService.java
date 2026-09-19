package com.liorshaya.policypilot.decision.service;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.decision.entity.CaseFixtureEntity;
import com.liorshaya.policypilot.decision.entity.DecisionEntity;
import com.liorshaya.policypilot.decision.repository.CaseFixtureRepository;
import com.liorshaya.policypilot.decision.repository.DecisionRepository;
import com.liorshaya.policypilot.decision.service.CaseInvalidException.CaseProblemView;
import com.liorshaya.policypilot.engine.CaseError;
import com.liorshaya.policypilot.engine.Decision;
import com.liorshaya.policypilot.engine.DecisionJson;
import com.liorshaya.policypilot.engine.Evaluation;
import com.liorshaya.policypilot.engine.RuleEngine;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Decisions (Brief FR-8, FR-9, FR-10; Document 2, Flow 2): one case, a list of cases or a seeded fixture set are
 * evaluated by the engine against a published version and stored with the input, the decision object, the version
 * and the timing. A case that fails case validation is not a decision and nothing is stored (Document 3, step 1);
 * a simulation is never stored at all (Document 3, Simulation).
 */
@Service
public class DecisionService {

    static final String ENTITY = "decision";

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private final DecisionRepository decisions;
    private final CaseFixtureRepository cases;
    private final SecurityEvents events;
    private final Clock clock;
    private final RuleEngine engine = new RuleEngine();

    public DecisionService(DecisionRepository decisions, CaseFixtureRepository cases, SecurityEvents events,
            Clock clock) {
        this.decisions = decisions;
        this.cases = cases;
        this.events = events;
        this.clock = clock;
    }

    /** Decides one case and stores it (Brief FR-8, FR-10). */
    @Transactional
    public DecisionView decide(PublishedVersion version, UUID sandboxId, ObjectNode input) {
        Evaluated evaluated = evaluate(version, input, "/case");
        DecisionEntity stored = decisions.save(row(version, sandboxId, null, input, evaluated));
        return view(version, stored);
    }

    /**
     * Decides a list of cases (Brief FR-9): every case is validated before anything is stored, so one invalid case
     * refuses the whole request (Document 3, step 1).
     */
    @Transactional
    public BatchResult decideAll(PublishedVersion version, UUID sandboxId, List<ObjectNode> inputs) {
        List<Evaluated> evaluations = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            evaluations.add(evaluate(version, inputs.get(i), "/cases/" + i));
        }
        List<DecisionEntity> rows = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            rows.add(row(version, sandboxId, null, inputs.get(i), evaluations.get(i)));
        }
        return batch(decisions.saveAll(rows), Map.of());
    }

    /**
     * Decides a seeded fixture set (Brief FR-9: the 200 cases with their aggregates); every decision names the case
     * it came from.
     */
    @Transactional
    public BatchResult decideFixtureSet(PublishedVersion version, UUID sandboxId, String fixtureSet) {
        List<CaseFixtureEntity> fixtures = cases.findByFixtureSetOrderByCaseNo(fixtureSet);
        List<DecisionEntity> rows = new ArrayList<>();
        Map<UUID, Integer> numbers = new LinkedHashMap<>();
        for (CaseFixtureEntity fixture : fixtures) {
            ObjectNode input = (ObjectNode) JSON.readTree(fixture.getFields());
            DecisionEntity row = row(version, sandboxId, fixture.getId(), input,
                    evaluate(version, input, "/fixtureSet"));
            numbers.put(row.getId(), fixture.getCaseNo());
            rows.add(row);
        }
        return batch(decisions.saveAll(rows), numbers);
    }

    /**
     * Seeds a case file as protected rows (Work Plan day 5: the 200 cases as protected rows): the file's own case
     * numbers and strata are kept, so a case is the same here as in {@code cases-200.json}.
     */
    @Transactional
    public int seedProtectedCases(String casesJson) {
        JsonNode file = JSON.readTree(casesJson);
        String fixtureSet = file.required("fixtureSet").stringValue();
        if (cases.existsByFixtureSet(fixtureSet)) {
            return 0;
        }
        List<CaseFixtureEntity> rows = file.required("cases").valueStream()
                .map(fixture -> new CaseFixtureEntity(UUID.randomUUID(), null, true, fixtureSet,
                        fixture.required("id").asInt(), "Case " + fixture.required("id").asInt(),
                        JSON.writeValueAsString(fixture.required("input")), null,
                        JSON.writeValueAsString(JSON.createArrayNode().add(fixture.path("stratum").asString("")))))
                .toList();
        cases.saveAll(rows);
        return rows.size();
    }

    /** Whether a fixture set is seeded, so an unknown name is refused before anything is evaluated. */
    @Transactional(readOnly = true)
    public boolean hasFixtureSet(String fixtureSet) {
        return cases.existsByFixtureSet(fixtureSet);
    }

    /** A stored decision of this sandbox (Document 2, {@code GET /decisions/{id}}). */
    @Transactional(readOnly = true)
    public Optional<DecisionView> decision(UUID id, UUID sandboxId, VersionNames names) {
        Optional<DecisionEntity> stored = decisions.findByIdAndSandboxId(id, sandboxId);
        if (stored.isEmpty() && decisions.existsById(id)) {
            events.authorizationDenied(ENTITY, sandboxId, id.toString());
        }
        return stored.map(row -> view(names.of(row.getRulesetVersionId()), row));
    }

    /**
     * Outcome counts and the top deciding rules over this sandbox's decisions on this version (Document 2, stats):
     * the latest decision of each case counts once, so running the fixture set twice does not double the numbers.
     */
    @Transactional(readOnly = true)
    public Aggregates stats(UUID versionId, UUID sandboxId) {
        List<DecisionEntity> stored = decisions
                .findBySandboxIdAndRulesetVersionIdOrderByDecidedAtAscIdAsc(sandboxId, versionId);
        Map<Object, DecisionEntity> latest = new LinkedHashMap<>();
        for (DecisionEntity row : stored) {
            latest.put(row.getCaseId() != null ? row.getCaseId() : row.getId(), row);
        }
        return aggregates(latest.values());
    }

    /**
     * A what-if evaluation (Document 3, Simulation; Document 2, simulate): the same version on a case with some
     * fields overridden. Nothing is stored, and the decision it is based on is left as it was.
     */
    public ObjectNode simulate(PublishedVersion version, ObjectNode input, ObjectNode overrides) {
        Evaluation evaluation = engine.simulate(version.compiled(), input, overrides);
        if (evaluation instanceof CaseError error) {
            throw caseInvalid(error, "/overrides");
        }
        return DecisionJson.toJson(evaluation);
    }

    /** The stored input of a decision, which a simulation re-evaluates with overrides. */
    public ObjectNode inputOf(DecisionView decision) {
        return (ObjectNode) JSON.readTree(decisions.findById(decision.id()).orElseThrow().getInput());
    }

    /** Re-evaluates a stored decision's input against its version: the replay that must equal what is stored. */
    public ObjectNode replay(PublishedVersion version, DecisionView decision) {
        return DecisionJson.toJson(engine.evaluate(version.compiled(), inputOf(decision)));
    }

    private Evaluated evaluate(PublishedVersion version, ObjectNode input, String pointer) {
        long started = System.nanoTime();
        Evaluation evaluation = engine.evaluate(version.compiled(), input);
        long micros = (System.nanoTime() - started) / 1_000;
        if (evaluation instanceof CaseError error) {
            throw caseInvalid(error, pointer);
        }
        return new Evaluated((Decision) evaluation, micros, clock.instant());
    }

    private CaseInvalidException caseInvalid(CaseError error, String pointer) {
        return new CaseInvalidException(error.problems().stream()
                .map(problem -> new CaseProblemView(pointer + "/" + problem.field(), problem.code().name()))
                .toList());
    }

    private DecisionEntity row(PublishedVersion version, UUID sandboxId, @Nullable UUID caseId, ObjectNode input,
            Evaluated evaluated) {
        Decision decision = evaluated.decision();
        return new DecisionEntity(UUID.randomUUID(), sandboxId, version.versionId(), caseId,
                JSON.writeValueAsString(input), decision.status().name(),
                decision.outcome() != null ? decision.outcome().json() : null, decision.decidingRuleId(),
                decision.errorCode() != null ? decision.errorCode().name() : null,
                DecisionJson.canonical(decision), evaluated.at(), evaluated.micros());
    }

    private BatchResult batch(List<DecisionEntity> rows, Map<UUID, Integer> caseNumbers) {
        List<CaseSummary> summaries = rows.stream()
                .map(row -> new CaseSummary(row.getId(), caseNumbers.get(row.getId()), row.getStatus(),
                        row.getOutcome(), row.getDecidingRuleId(), flagsOf(row)))
                .toList();
        return new BatchResult(aggregates(rows), summaries);
    }

    private static List<String> flagsOf(DecisionEntity row) {
        return JSON.readTree(row.getDecision()).path("flags").valueStream()
                .map(flag -> flag.path("code").asString())
                .toList();
    }

    private static Aggregates aggregates(Iterable<DecisionEntity> rows) {
        Map<String, Integer> outcomes = new LinkedHashMap<>(Map.of("approve", 0, "reject", 0, "refer", 0));
        Map<String, Integer> deciding = new LinkedHashMap<>();
        int errors = 0;
        int decisions = 0;
        for (DecisionEntity row : rows) {
            decisions++;
            if (row.getOutcome() == null) {
                errors++;
                continue;
            }
            outcomes.merge(row.getOutcome(), 1, Integer::sum);
            if (row.getDecidingRuleId() != null) {
                deciding.merge(row.getDecidingRuleId(), 1, Integer::sum);
            }
        }
        List<Aggregates.TopRule> top = deciding.entrySet().stream()
                .map(entry -> new Aggregates.TopRule(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(Aggregates.TopRule::count).reversed()
                        .thenComparing(Aggregates.TopRule::ruleId))
                .limit(Aggregates.TOP_RULES)
                .toList();
        return new Aggregates(outcomes, errors, top, decisions);
    }

    private DecisionView view(PublishedVersion version, DecisionEntity row) {
        ObjectNode decision = (ObjectNode) JSON.readTree(row.getDecision());
        Integer caseNo = row.getCaseId() == null ? null
                : cases.findById(row.getCaseId()).map(CaseFixtureEntity::getCaseNo).orElse(null);
        return new DecisionView(row.getId(), caseNo, version.domain(), version.versionNo(), version.versionId(),
                row.getDecidedAt(), row.getDurationMicros(), decision);
    }

    /** One evaluation of one case: what the engine decided, how long it took and when. */
    private record Evaluated(Decision decision, long micros, Instant at) {}

    /** How a decision's version is named when it is read back; the ruleset module knows, the decision row does not. */
    @FunctionalInterface
    public interface VersionNames {

        PublishedVersion of(UUID versionId);
    }
}
