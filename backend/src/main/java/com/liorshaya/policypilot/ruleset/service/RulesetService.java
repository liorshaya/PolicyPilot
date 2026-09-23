package com.liorshaya.policypilot.ruleset.service;

import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.engine.CompiledRuleSet;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.diff.StructuralDiff;
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
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
 * because a published version never changes (the trigger). Its embedding status is the one exception, and it moves
 * only through the methods below that {@code rag} calls (Document 2, RAG pipeline, Embedding).
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
    private final ApplicationEventPublisher publications;
    private final Clock clock;
    private final RuleSetValidator validator = new RuleSetValidator();
    private final RuleSetMapper mapper = new RuleSetMapper();
    private final Map<UUID, CompiledRuleSet> compiled = new ConcurrentHashMap<>();

    public RulesetService(RulesetRepository rulesets, RulesetVersionRepository versions, RuleRepository rules,
            PolicyService policies, AuditLog audit, SecurityEvents events, ApplicationEventPublisher publications,
            Clock clock) {
        this.rulesets = rulesets;
        this.versions = versions;
        this.rules = rules;
        this.policies = policies;
        this.audit = audit;
        this.events = events;
        this.publications = publications;
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
     * The structural diff of two versions of one rule set the sandbox can see (Document 3, Structural diff); empty when
     * it cannot see the rule set or has no such version.
     */
    @Transactional(readOnly = true)
    public Optional<StructuralDiff> diff(UUID rulesetId, int from, int to, UUID sandboxId) {
        Optional<Found> before = found(rulesetId, from, sandboxId);
        if (before.isEmpty()) {
            return Optional.empty();
        }
        return found(rulesetId, to, sandboxId).map(after -> StructuralDiff.between(
                mapper.toRuleSet(RuleSetDocuments.read(before.get().version().getRulesJson())),
                mapper.toRuleSet(RuleSetDocuments.read(after.version().getRulesJson()))));
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
            RuleSet validated = validate(document, version.getPolicyVersionId(), ValidationContext.ANALYST_EDIT)
                    .ruleSet();
            return Optional.of(fork(ruleset, version, sandboxId, document, validated));
        }
        if (!version.isDraft()) {
            throw new VersionStatusException("version " + versionNo + " is " + version.getStatus());
        }
        ValidationResult result = validate(document, version.getPolicyVersionId(), ValidationContext.ANALYST_EDIT);
        version.replace(RuleSetDocuments.json(document), RuleSetDocuments.fieldSchema(document));
        markStale(version);
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
        RulesetVersionEntity version = found.get().version();
        if (!version.isDraft()) {
            throw new VersionStatusException("version " + versionNo + " is " + version.getStatus());
        }
        requireReviewAllowsPublishing(ReviewJson.read(version.getReviewJson()));
        return Optional.of(publish(ruleset, version, sandboxId.toString()));
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

    /** A published version by its row id, for a stored decision that names it; scoped to the caller's sandbox. */
    @Transactional(readOnly = true)
    public Optional<PublishedVersion> publishedById(UUID versionId, UUID sandboxId) {
        return versions.findById(versionId).flatMap(version -> rulesets
                .findVisible(version.getRulesetId(), sandboxId)
                .map(ruleset -> new PublishedVersion(version.getId(), ruleset.getId(), ruleset.getDomain(),
                        version.getVersionNo(), compiled(version))));
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
     * Stores the review of a DRAFT, replacing any earlier one with its acknowledgements (Document 2, Flow 1 and
     * {@code POST .../review}). Empty when the sandbox cannot see the version.
     */
    @Transactional
    public Optional<VersionView> recordReview(UUID rulesetId, int versionNo, UUID sandboxId, Review review) {
        Optional<Found> found = found(rulesetId, versionNo, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RulesetVersionEntity version = requireOwnDraft(found.get(), sandboxId);
        version.review(ReviewJson.write(review));
        return Optional.of(view(found.get().ruleset(), version));
    }

    /**
     * Acknowledges one review finding of a DRAFT (Document 2, {@code POST .../findings/{findingId}/acknowledge};
     * Document 3, Publishing gate): a gap needs its resolution and writes a GAP_ACKNOWLEDGED audit entry, an error
     * needs a note, the other kinds need neither.
     * Empty when the sandbox cannot see the version or it has no such finding.
     *
     * @throws IllegalArgumentException when the resolution or the note the finding's kind needs is missing
     */
    @Transactional
    public Optional<VersionView> acknowledge(UUID rulesetId, int versionNo, UUID sandboxId, String findingId,
            @Nullable GapResolution resolution,
            @Nullable String note) {
        Optional<Found> found = found(rulesetId, versionNo, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RulesetVersionEntity version = requireOwnDraft(found.get(), sandboxId);
        Review review = ReviewJson.read(version.getReviewJson());
        Optional<ReviewFinding> finding = review == null ? Optional.empty() : review.finding(findingId);
        if (finding.isEmpty()) {
            return Optional.empty();
        }
        FindingKind kind = finding.get().kind();
        String trimmed = note == null || note.isBlank() ? null : note.strip();
        if (kind == FindingKind.GAP && resolution == null) {
            throw new IllegalArgumentException("a gap is acknowledged with a resolution");
        }
        if (kind != FindingKind.GAP && resolution != null) {
            throw new IllegalArgumentException("only a gap takes a resolution");
        }
        if (kind.isError() && trimmed == null) {
            throw new IllegalArgumentException("an error is overridden with a note");
        }
        String actor = sandboxId.toString();
        Acknowledgement acknowledgement = new Acknowledgement(resolution, trimmed, actor, clock.instant());
        version.review(ReviewJson.write(review.with(finding.get().acknowledgedWith(acknowledgement))));
        if (kind == FindingKind.GAP) {
            ObjectNode details = ReviewJson.finding(finding.get().acknowledgedWith(acknowledgement));
            details.put("rulesetId", found.get().ruleset().getId().toString()).put("versionNo", versionNo);
            audit.append(AuditAction.GAP_ACKNOWLEDGED, actor, version.getId(), details);
        }
        return Optional.of(view(found.get().ruleset(), version));
    }

    /**
     * Publishes an approved change as the next version of its base's rule set (Document 3, Version lineage; Document 2,
     * approve), inside the caller's transaction: the patched document becomes the version after the base, the base
     * its parent, recorded in one CHANGE_APPROVED audit entry with the caller's details. A protected base is approved
     * into the sandbox's own copy of its rule set, whose version 1 is the base as published and version 2 the change,
     * so the protected version never changes.
     *
     * @param document the base with the change applied and every pending provenance rewritten to analyst
     * @param retired the rule ids the change removed
     * @throws VersionStatusException when the base is no longer the latest version of its rule set, or the sandbox
     *     already has its copy of the protected rule set
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public VersionView publishApproved(UUID baseVersionId, UUID sandboxId, JsonNode document,
            Collection<String> retired, UUID changeRequestId, ObjectNode details) {
        RulesetVersionEntity base = versions.findById(baseVersionId).orElseThrow();
        RulesetEntity ruleset = rulesets.findVisible(base.getRulesetId(), sandboxId).orElseThrow();
        String actor = sandboxId.toString();
        RulesetEntity target = ruleset;
        RulesetVersionEntity parent = base;
        if (ruleset.isProtectedRow()) {
            if (rulesets.findBySandboxIdAndForkedFromId(sandboxId, ruleset.getId()).isPresent()) {
                throw new VersionStatusException("the sandbox already has its copy of this rule set; propose on it");
            }
            target = rulesets.save(new RulesetEntity(UUID.randomUUID(), sandboxId, false, ruleset.getId(),
                    ruleset.getName(), ruleset.getDomain(), ruleset.getDefaultOutcome(), clock.instant()));
            parent = versions.save(new RulesetVersionEntity(UUID.randomUUID(), target.getId(), 1,
                    base.getPolicyVersionId(), base.getRulesJson(),
                    RuleSetDocuments.fieldSchema(RuleSetDocuments.read(base.getRulesJson())), base.getId()));
            parent.retire(base.getRetiredIds());
            publish(target, parent, actor, AuditAction.PUBLISH, null,
                    JSON.createObjectNode().put("forkedFromVersionId", base.getId().toString()));
        } else if (versions.findByRulesetIdOrderByVersionNo(ruleset.getId()).getLast().getVersionNo()
                != base.getVersionNo()) {
            throw new VersionStatusException("version " + base.getVersionNo() + " is no longer the latest");
        }
        RulesetVersionEntity next = versions.save(new RulesetVersionEntity(UUID.randomUUID(), target.getId(),
                parent.getVersionNo() + 1, base.getPolicyVersionId(), RuleSetDocuments.json(document),
                RuleSetDocuments.fieldSchema(document), parent.getId()));
        next.retire(JSON.valueToTree(retired).toString());
        return publish(target, next, actor, AuditAction.CHANGE_APPROVED, changeRequestId, details);
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

    /**
     * The rule ids every version of the rule set has retired, which an added rule may never take again (Document 3,
     * Change Patches: "retired ids are never reused"); empty when the sandbox cannot see the rule set.
     */
    @Transactional(readOnly = true)
    public List<String> retiredIds(UUID rulesetId, UUID sandboxId) {
        return rulesets.findVisible(rulesetId, sandboxId).stream()
                .flatMap(ruleset -> versions.findByRulesetIdOrderByVersionNo(ruleset.getId()).stream())
                .flatMap(version -> JSON.readTree(version.getRetiredIds()).valueStream())
                .map(JsonNode::asString).distinct().toList();
    }

    /**
     * What retrieval searches on a version the sandbox can see (Document 4, Retrieval Pipeline, Scoping): its rule set
     * and its paragraphs; empty when the sandbox cannot see it, and a status conflict unless its embedding is
     * {@code READY} (Document 4, Embedding: questions on a version still embedding are refused).
     */
    @Transactional(readOnly = true)
    public Optional<EmbeddingSource> corpus(UUID rulesetId, int versionNo, UUID sandboxId) {
        return found(rulesetId, versionNo, sandboxId).map(found -> {
            RulesetVersionEntity version = found.version();
            if (!EmbeddingStatus.READY.name().equals(version.getEmbeddingStatus())) {
                throw new VersionStatusException("version " + versionNo + " is " + version.getStatus()
                        + " with embedding " + version.getEmbeddingStatus());
            }
            RuleSet ruleSet = mapper.toRuleSet(RuleSetDocuments.read(version.getRulesJson()));
            return new EmbeddingSource(version.getId(), ruleSet, policy(version.getPolicyVersionId()).paragraphs());
        });
    }

    /**
     * Claims a {@code PENDING} version for embedding, moving it to {@code EMBEDDING}, and returns what its corpus is
     * made of; empty when the version is not {@code PENDING}, a DRAFT or one another run has already claimed.
     */
    @Transactional
    public Optional<EmbeddingSource> startEmbedding(UUID versionId) {
        int claimed = versions.moveEmbeddingStatus(versionId, names(EmbeddingStatus.PENDING),
                EmbeddingStatus.EMBEDDING.name());
        if (claimed == 0) {
            return Optional.empty();
        }
        RulesetVersionEntity version = versions.findById(versionId).orElseThrow();
        RuleSet ruleSet = mapper.toRuleSet(RuleSetDocuments.read(version.getRulesJson()));
        return Optional.of(new EmbeddingSource(versionId, ruleSet, policy(version.getPolicyVersionId()).paragraphs()));
    }

    /** Ends an embedding the caller claimed: {@code READY} with its chunks stored, or {@code FAILED}. */
    @Transactional
    public void finishEmbedding(UUID versionId, boolean ready) {
        EmbeddingStatus to = ready ? EmbeddingStatus.READY : EmbeddingStatus.FAILED;
        versions.moveEmbeddingStatus(versionId, names(EmbeddingStatus.EMBEDDING), to.name());
    }

    /**
     * At startup, every version to embed again (Document 2, RAG pipeline, Embedding): {@code PENDING} ones, and
     * {@code FAILED} ones, retried once per start. An {@code EMBEDDING} version is left alone: during a rolling deploy
     * the old instance may still be embedding it, and a clean stop ends its run as {@code FAILED} anyway.
     */
    @Transactional
    public List<UUID> resumeEmbeddings() {
        versions.moveEveryEmbeddingStatus(names(EmbeddingStatus.FAILED), EmbeddingStatus.PENDING.name());
        return versions.findIdsByEmbeddingStatus(EmbeddingStatus.PENDING.name());
    }

    private static List<String> names(EmbeddingStatus first, EmbeddingStatus... rest) {
        return EnumSet.of(first, rest).stream().map(EmbeddingStatus::name).toList();
    }

    /**
     * The gate of Document 2, Flow 1: a draft is published only after a review that is DONE, with every error, gap
     * and injection acknowledged; Document 5, RT-05 depends on it.
     */
    private static void requireReviewAllowsPublishing(@Nullable Review review) {
        if (review == null) {
            throw new FindingsUnresolvedException(List.of(new RulesetProblem("/review", "REVIEW_MISSING")));
        }
        if (review.status() != ReviewStatus.DONE) {
            throw new FindingsUnresolvedException(List.of(new RulesetProblem("/review", review.status().name())));
        }
        List<RulesetProblem> open = review.findings().stream()
                .filter(ReviewFinding::blocking)
                .map(finding -> new RulesetProblem("/review/findings/" + finding.id(), finding.kind().name()))
                .toList();
        if (!open.isEmpty()) {
            throw new FindingsUnresolvedException(open);
        }
    }

    /** The version the sandbox may review or acknowledge on: its own rule set's DRAFT. */
    private RulesetVersionEntity requireOwnDraft(Found found, UUID sandboxId) {
        if (found.ruleset().isProtectedRow()) {
            events.protectedWriteAttempt(ENTITY, sandboxId);
            throw new VersionStatusException("a protected version is never reviewed through the API");
        }
        if (!found.version().isDraft()) {
            throw new VersionStatusException(
                    "version " + found.version().getVersionNo() + " is " + found.version().getStatus());
        }
        return found.version();
    }

    /** An edited draft keeps its findings on screen, but they no longer describe the document (Document 2). */
    private static void markStale(RulesetVersionEntity version) {
        Review review = ReviewJson.read(version.getReviewJson());
        if (review != null && review.status() != ReviewStatus.STALE) {
            version.review(ReviewJson.write(review.stale()));
        }
    }

    private VersionView publish(RulesetEntity ruleset, RulesetVersionEntity version, String actor) {
        return publish(ruleset, version, actor, AuditAction.PUBLISH, null, JSON.createObjectNode());
    }

    /**
     * Publishes a DRAFT: validated in the PUBLISH context, compiled, snapshotted as {@code rule} rows, and recorded in
     * one audit entry of the given action, whose details are the publish's own and the caller's.
     */
    private VersionView publish(RulesetEntity ruleset, RulesetVersionEntity version, String actor, AuditAction action,
            @Nullable UUID changeRequestId, ObjectNode more) {
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
        Review review = ReviewJson.read(version.getReviewJson());
        if (review != null) {
            // the analyst's acknowledgements and the warnings left open travel with the publish (Document 3)
            details.set("review", ReviewJson.tree(review));
        }
        details.setAll(more);
        audit.append(action, actor, version.getId(), changeRequestId, details);
        // delivered after the commit, so the embedding job never sees a version that might still roll back
        publications.publishEvent(new VersionPublished(version.getId()));
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
            markStale(latest);
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
        return versions.findByRulesetIdAndVersionNo(rulesetId, versionNo)
                .map(version -> new Found(ruleset.get(), version));
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
                findings, ReviewJson.read(version.getReviewJson()));
    }

    /** A rule set the caller may see and one of its versions. */
    private record Found(RulesetEntity ruleset, RulesetVersionEntity version) {}
}
