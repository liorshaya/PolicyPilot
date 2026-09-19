package com.liorshaya.policypilot.ruleset.service;

import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.validation.Finding;
import com.liorshaya.policypilot.rules.validation.RuleSetValidator;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.rules.validation.ValidationResult;
import com.liorshaya.policypilot.ruleset.entity.RuleEntity;
import com.liorshaya.policypilot.ruleset.entity.RulesetEntity;
import com.liorshaya.policypilot.ruleset.entity.RulesetVersionEntity;
import com.liorshaya.policypilot.ruleset.repository.RuleRepository;
import com.liorshaya.policypilot.ruleset.repository.RulesetRepository;
import com.liorshaya.policypilot.ruleset.repository.RulesetVersionRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Rule set versions (Document 2, API Surface and Data Model; Work Plan day 5): reading a version with its findings,
 * replacing the rules of a DRAFT, and publishing in one transaction (validate, compile, snapshot the rules, write the
 * audit entry). A version is read only for the sandbox that owns its rule set or, when the rule set is protected, for
 * every sandbox; a write against a protected rule set is refused and lands on the sandbox's own copy instead
 * (Document 5, Authorization). The compiled document of a published version is cached per version id, which is safe
 * because a published version never changes (the V4 trigger).
 */
@Service
public class RulesetService {

    static final String ENTITY = "ruleset";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** The actor of the seeded demo rows (Document 5, Why no user accounts). */
    public static final String DEMO_ACTOR = "demo-analyst";

    private final RulesetRepository rulesets;
    private final RulesetVersionRepository versions;
    private final RuleRepository rules;
    private final PolicyService policies;
    private final AuditLog audit;
    private final SecurityEvents events;
    private final Clock clock;
    private final RuleSetValidator validator = new RuleSetValidator();
    private final RuleSetMapper mapper = new RuleSetMapper();
    private final Map<UUID, CompiledRuleSet> compiled = new ConcurrentHashMap<>();

    public RulesetService(RulesetRepository rulesets, RulesetVersionRepository versions, RuleRepository rules,
            PolicyService policies, AuditLog audit, SecurityEvents events, Clock clock) {
        this.rulesets = rulesets;
        this.versions = versions;
        this.rules = rules;
        this.policies = policies;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    /** Every rule set the sandbox sees: the protected ones and its own (Document 2, {@code GET /rulesets}). */
    @Transactional(readOnly = true)
    public List<RulesetView> visible(UUID sandboxId) {
        return rulesets.findAllVisible(sandboxId).stream().map(this::view).toList();
    }

    /** The seeded rule sets, oldest first. */
    @Transactional(readOnly = true)
    public List<RulesetView> protectedRulesets() {
        return rulesets.findByProtectedRowTrueOrderByCreatedAt().stream().map(this::view).toList();
    }

    /** One version with its document and its findings; empty when the sandbox cannot see it. */
    @Transactional(readOnly = true)
    public Optional<VersionView> version(UUID rulesetId, int versionNo, UUID sandboxId) {
        return found(rulesetId, versionNo, sandboxId).map(found -> view(found.ruleset(), found.version()));
    }

    /**
     * Replaces the document of a DRAFT after manual edits (Document 2, {@code PUT .../rules}; Brief FR-6). A write
     * against a protected rule set forks the sandbox's own copy, whose version 1 is a DRAFT holding the edit; the
     * protected rows never change.
     */
    @Transactional
    public Optional<VersionView> replaceRules(UUID rulesetId, int versionNo, UUID sandboxId, JsonNode document) {
        Optional<Found> found = found(rulesetId, versionNo, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RulesetEntity ruleset = found.get().ruleset();
        RulesetVersionEntity version = found.get().version();
        requireSameDocumentId(ruleset, document);
        if (ruleset.isProtectedRow()) {
            events.protectedWriteAttempt(ENTITY, sandboxId);
            RuleSet validated = validate(document, version.getPolicyVersionId(), ValidationContext.ANALYST_EDIT).ruleSet();
            return Optional.of(fork(ruleset, version, sandboxId, document, validated));
        }
        if (!version.isDraft()) {
            throw new VersionStatusException("version " + versionNo + " is " + version.getStatus());
        }
        ValidationResult result = validate(document, version.getPolicyVersionId(), ValidationContext.ANALYST_EDIT);
        version.replace(RuleSetDocuments.json(document), RuleSetDocuments.fieldSchema(document));
        ruleset.describe(result.ruleSet().name(), result.ruleSet().defaults().outcome().json());
        return Optional.of(view(ruleset, version, result.findings()));
    }

    /**
     * Publishes a DRAFT in one transaction (Document 2, API Surface; Brief FR-7): the document is validated in the
     * PUBLISH context, compiled, snapshotted as {@code rule} rows and recorded in one audit entry that lists the
     * warnings left open (Document 3, Publishing gate).
     */
    @Transactional
    public Optional<VersionView> publish(UUID rulesetId, int versionNo, UUID sandboxId) {
        Optional<Found> found = found(rulesetId, versionNo, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RulesetEntity ruleset = found.get().ruleset();
        if (ruleset.isProtectedRow()) {
            events.protectedWriteAttempt(ENTITY, sandboxId);
            throw new VersionStatusException("a protected version is never published through the API");
        }
        return Optional.of(publish(ruleset, found.get().version(), sandboxId.toString()));
    }

    /**
     * A published version ready to decide (Brief FR-7: only published versions decide cases); empty when the sandbox
     * cannot see it, and a status conflict when the version is not published.
     */
    @Transactional(readOnly = true)
    public Optional<PublishedVersion> published(UUID rulesetId, int versionNo, UUID sandboxId) {
        return found(rulesetId, versionNo, sandboxId).map(found -> {
            RulesetVersionEntity version = found.version();
            if (version.isDraft()) {
                throw new VersionStatusException("version " + versionNo + " is " + version.getStatus());
            }
            return new PublishedVersion(version.getId(), found.ruleset().getId(), found.ruleset().getDomain(),
                    version.getVersionNo(), compiled(version));
        });
    }

    /**
     * A first DRAFT in a new rule set of the sandbox, written from a policy version: what the authoring prompt stores
     * on day 7, and what a test uses to reach a draft before that route exists.
     */
    @Transactional
    public VersionView createDraft(UUID sandboxId, UUID policyVersionId, JsonNode document, ValidationContext context,
            Set<String> modelRuleIds) {
        RuleSet validated = validate(document, policyVersionId, context, modelRuleIds).ruleSet();
        RulesetEntity ruleset = rulesets.save(new RulesetEntity(UUID.randomUUID(), sandboxId, false, null,
                validated.name(), validated.id(), validated.defaults().outcome().json(), clock.instant()));
        RulesetVersionEntity version = versions.save(new RulesetVersionEntity(UUID.randomUUID(), ruleset.getId(), 1,
                policyVersionId, RuleSetDocuments.json(document), RuleSetDocuments.fieldSchema(document), null));
        return view(ruleset, version);
    }

    /**
     * Seeds the demo rule set as a protected, published version 1 with its audit entry (Work Plan day 5): no sandbox
     * owns it, every sandbox reads it and none may write to it.
     */
    @Transactional
    public RulesetView seedProtected(UUID policyVersionId, String documentJson) {
        JsonNode document = mapper.readTree(documentJson);
        RuleSet validated = validate(document, policyVersionId, ValidationContext.PUBLISH).ruleSet();
        RulesetEntity ruleset = rulesets.save(new RulesetEntity(UUID.randomUUID(), null, true, null, validated.name(),
                validated.id(), validated.defaults().outcome().json(), clock.instant()));
        RulesetVersionEntity version = versions.save(new RulesetVersionEntity(UUID.randomUUID(), ruleset.getId(), 1,
                policyVersionId, RuleSetDocuments.json(document), RuleSetDocuments.fieldSchema(document), null));
        publish(ruleset, version, DEMO_ACTOR);
        return view(ruleset);
    }

    private VersionView publish(RulesetEntity ruleset, RulesetVersionEntity version, String actor) {
        if (!version.isDraft()) {
            throw new VersionStatusException("version " + version.getVersionNo() + " is " + version.getStatus());
        }
        ObjectNode document = RuleSetDocuments.read(version.getRulesJson());
        PolicyVersionRef policy = policy(version.getPolicyVersionId());
        ValidationResult result = validate(document, policy, ValidationContext.PUBLISH, Set.of());
        List<RuleEntity> rows = RuleSetDocuments.rows(version.getId(), result.ruleSet(),
                (ArrayNode) document.get("rules"), policy);
        version.publish(clock.instant(), actor);
        rules.saveAll(rows);
        compiled.put(version.getId(), CompiledRuleSet.compile(result.ruleSet()));
        ObjectNode details = JSON.createObjectNode();
        details.put("rulesetId", ruleset.getId().toString())
                .put("versionNo", version.getVersionNo())
                .put("rules", rows.size())
                .set("warnings", RuleSetDocuments.warnings(result.findings()));
        audit.append(AuditAction.PUBLISH, actor, version.getId(), details);
        return view(ruleset, version, result.findings());
    }

    private VersionView fork(RulesetEntity original, RulesetVersionEntity source, UUID sandboxId, JsonNode document,
            RuleSet validated) {
        Optional<RulesetEntity> existing = rulesets.findBySandboxIdAndForkedFromId(sandboxId, original.getId());
        if (existing.isPresent()) {
            RulesetEntity copy = existing.get();
            RulesetVersionEntity latest = versions.findByRulesetIdOrderByVersionNo(copy.getId()).getLast();
            if (!latest.isDraft()) {
                throw new VersionStatusException("version " + latest.getVersionNo() + " is " + latest.getStatus());
            }
            latest.replace(RuleSetDocuments.json(document), RuleSetDocuments.fieldSchema(document));
            copy.describe(validated.name(), validated.defaults().outcome().json());
            return view(copy, latest);
        }
        RulesetEntity copy = rulesets.save(new RulesetEntity(UUID.randomUUID(), sandboxId, false, original.getId(),
                validated.name(), original.getDomain(), validated.defaults().outcome().json(), clock.instant()));
        RulesetVersionEntity draft = versions.save(new RulesetVersionEntity(UUID.randomUUID(), copy.getId(), 1,
                source.getPolicyVersionId(), RuleSetDocuments.json(document), RuleSetDocuments.fieldSchema(document),
                source.getId()));
        return view(copy, draft);
    }

    private Optional<Found> found(UUID rulesetId, int versionNo, UUID sandboxId) {
        Optional<RulesetEntity> ruleset = rulesets.findVisible(rulesetId, sandboxId);
        if (ruleset.isEmpty()) {
            if (rulesets.existsById(rulesetId)) {
                events.authorizationDenied(ENTITY, sandboxId, rulesetId.toString());
            }
            return Optional.empty();
        }
        return versions.findByRulesetIdAndVersionNo(rulesetId, versionNo).map(version -> new Found(ruleset.get(), version));
    }

    private void requireSameDocumentId(RulesetEntity ruleset, JsonNode document) {
        if (!ruleset.getDomain().equals(document.path("id").asString(""))) {
            throw new RulesetInvalidException(List.of(new RulesetProblem("/id", RulesetProblem.ID_CHANGED)));
        }
    }

    private ValidationResult validate(JsonNode document, UUID policyVersionId, ValidationContext context) {
        return validate(document, policyVersionId, context, Set.of());
    }

    private ValidationResult validate(JsonNode document, UUID policyVersionId, ValidationContext context,
            Set<String> modelRuleIds) {
        return validate(document, policy(policyVersionId), context, modelRuleIds);
    }

    private ValidationResult validate(JsonNode document, PolicyVersionRef policy, ValidationContext context,
            Set<String> modelRuleIds) {
        ValidationResult result = validator.validate(document, context, policy.texts(), modelRuleIds);
        if (result.hasErrors()) {
            throw new RulesetInvalidException(RuleSetDocuments.problems(result.findings()));
        }
        return result;
    }

    private PolicyVersionRef policy(UUID policyVersionId) {
        return policies.version(policyVersionId)
                .orElseThrow(() -> new IllegalStateException("no policy version " + policyVersionId));
    }

    private CompiledRuleSet compiled(RulesetVersionEntity version) {
        return compiled.computeIfAbsent(version.getId(),
                ignored -> CompiledRuleSet.compile(mapper.toRuleSet(RuleSetDocuments.read(version.getRulesJson()))));
    }

    private RulesetView view(RulesetEntity ruleset) {
        List<RulesetView.VersionSummary> summaries = versions.findByRulesetIdOrderByVersionNo(ruleset.getId()).stream()
                .map(version -> new RulesetView.VersionSummary(version.getVersionNo(),
                        VersionStatus.valueOf(version.getStatus())))
                .toList();
        UUID policyId = versions.findByRulesetIdOrderByVersionNo(ruleset.getId()).stream()
                .findFirst()
                .map(version -> policy(version.getPolicyVersionId()).documentId())
                .orElse(null);
        return new RulesetView(ruleset.getId(), ruleset.getName(), ruleset.getDomain(), ruleset.isProtectedRow(),
                ruleset.getForkedFromId(), policyId, summaries);
    }

    private VersionView view(RulesetEntity ruleset, RulesetVersionEntity version) {
        ObjectNode document = RuleSetDocuments.read(version.getRulesJson());
        ValidationContext context = version.isDraft() ? ValidationContext.ANALYST_EDIT : ValidationContext.PUBLISH;
        List<Finding> findings = validator
                .validate(document, context, policy(version.getPolicyVersionId()).texts(), Set.of())
                .findings();
        return view(ruleset, version, findings);
    }

    private VersionView view(RulesetEntity ruleset, RulesetVersionEntity version, List<Finding> findings) {
        return new VersionView(ruleset.getId(), ruleset.getName(), ruleset.getDomain(), ruleset.isProtectedRow(),
                ruleset.getForkedFromId(), version.getId(), version.getVersionNo(),
                VersionStatus.valueOf(version.getStatus()), version.getPolicyVersionId(), version.getParentVersionId(),
                version.getPublishedAt(), version.getPublishedBy(), RuleSetDocuments.read(version.getRulesJson()),
                findings);
    }

    /** A rule set the caller may see and one of its versions. */
    private record Found(RulesetEntity ruleset, RulesetVersionEntity version) {}
}
