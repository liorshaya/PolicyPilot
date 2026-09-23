package com.liorshaya.policypilot.change.service;

import com.liorshaya.policypilot.ai.change.ChangeAnalysis;
import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeBase;
import com.liorshaya.policypilot.ai.service.ChangeService;
import com.liorshaya.policypilot.ai.service.Proposal;
import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.change.entity.ChangeRequestEntity;
import com.liorshaya.policypilot.change.repository.ChangeRequestRepository;
import com.liorshaya.policypilot.decision.service.DecisionService;
import com.liorshaya.policypilot.decision.service.Regression;
import com.liorshaya.policypilot.rules.diff.StructuralDiff;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.rules.patch.Patches;
import com.liorshaya.policypilot.ruleset.service.PublishedVersion;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Change requests (Document 2, Flow 4; Work Plan days 12 and 13): the impact analysis, the model's proposal and its
 * validation, and, only for a proposal that validated, its regression and its diff, then the stored PROPOSED request
 * with its CHANGE_PROPOSED audit entry in one transaction; then a person's approval or rejection. A proposal that fails
 * Patch validation after its repairs or is refused by the proposal validator is never stored (Document 3, Patch
 * validation).
 *
 * <p>The actor is the sandbox (Document 5, Why no user accounts). The model is called outside any transaction, so a
 * slow provider holds no connection.
 */
@Service
public class ChangeRequestService {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RuleSetMapper DSL = new RuleSetMapper();

    private final ChangeAnalysis analysis;
    private final ChangeService proposer;
    private final DecisionService decisions;
    private final RulesetService rulesets;
    private final ChangeRequestRepository requests;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ChangeRequestService(ChangeAnalysis analysis, ChangeService proposer, DecisionService decisions,
            RulesetService rulesets, ChangeRequestRepository requests, AuditLog audit,
            TransactionTemplate transactions, Clock clock) {
        this.analysis = analysis;
        this.proposer = proposer;
        this.decisions = decisions;
        this.rulesets = rulesets;
        this.requests = requests;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * The version a change is proposed against: empty when the sandbox cannot see it, and a status conflict when it is
     * not PUBLISHED or its embedding is not READY (Document 2, API Surface: both before any stream opens).
     */
    public Optional<ChangeBase> base(UUID rulesetId, int versionNo, UUID sandboxId) {
        return analysis.base(rulesetId, versionNo, sandboxId);
    }

    /**
     * Analyzes the request, asks for a proposal and, when it validates, runs its regression and stores it.
     *
     * @param request the request as the analyst wrote it, normalized
     * @throws com.liorshaya.policypilot.ai.LlmUnavailableException when the embedding or the model failed
     * @throws com.liorshaya.policypilot.ai.LlmMalformedOutputException when no answer was a JSON object
     */
    public Submitted submit(ChangeBase base, String request, UUID sandboxId, ChangeProgress progress) {
        progress.analyzing();
        Candidates candidates = analysis.candidates(base, request);
        Proposal proposal = proposer.propose(base, request, candidates, stage -> {
            switch (stage) {
                case PROPOSING -> progress.proposing(candidates);
                case VALIDATING -> progress.validating();
            }
        });
        if (!proposal.valid()) {
            return new Submitted.NotStored(proposal);
        }
        progress.regression();
        JsonNode copy = Objects.requireNonNull(proposal.validation().patched());
        Regression regression = decisions.regression(base.versionId(), sandboxId, copy);
        StructuralDiff diff = StructuralDiff.between(base.ruleSet(), DSL.toRuleSet(copy));
        return new Submitted.Stored(Objects.requireNonNull(
                transactions.execute(status -> store(base, request, proposal, diff, regression, sandboxId))));
    }

    /**
     * Approves a PROPOSED request of this sandbox (Document 2, approve), in one transaction: every pending provenance
     * of its patches becomes analyst (Document 3, Provenance), the patched base is published as the next version, into
     * the sandbox's own copy of the rule set when the base is protected, the request becomes APPROVED with that version
     * as its result, and the new version carries a CHANGE_APPROVED audit entry with the request, the note, the diff
     * and the regression report. Empty for a request this sandbox does not have.
     *
     * @param note the approver's note, or null
     * @throws VersionStatusException when the request is not PROPOSED, or its base is no longer the latest version of
     *     its rule set, or the sandbox already has its copy of a protected one
     */
    @Transactional
    public Optional<ChangeDecision> approve(UUID id, UUID sandboxId, @Nullable String note) {
        Optional<ChangeRequestEntity> found = requests.findByIdAndSandboxId(id, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ChangeRequestEntity request = proposed(found.get());
        PublishedVersion base = rulesets.publishedById(request.getBaseVersionId(), sandboxId).orElseThrow();
        RuleSet before = base.compiled().ruleSet();
        Patches.Applied applied = Patches.apply(DSL.toJson(before), Patches.approved(
                JSON.readTree(request.getPatchesJson()), request.getRequestText(), sandboxId.toString()));
        ObjectNode details = JSON.createObjectNode()
                .put("baseVersionId", base.versionId().toString())
                .put("requestText", request.getRequestText())
                .put("note", note);
        details.set("diff", StructuralDiff.between(before, DSL.toRuleSet(applied.document())).toJson());
        details.set("regression", JSON.readTree(request.getRegressionJson()));
        VersionView published = rulesets.publishApproved(base.versionId(), sandboxId, applied.document(),
                applied.retired(), id, details);
        request.approve(published.versionId(), clock.instant());
        return Optional.of(ChangeDecision.of(request, published));
    }

    /**
     * Rejects a PROPOSED request of this sandbox (Document 2, reject): nothing is published, and a CHANGE_REJECTED
     * audit entry on the base version holds the note. Empty for a request this sandbox does not have.
     *
     * @param note the reviewer's note, or null
     * @throws VersionStatusException when the request is not PROPOSED
     */
    @Transactional
    public Optional<ChangeDecision> reject(UUID id, UUID sandboxId, @Nullable String note) {
        Optional<ChangeRequestEntity> found = requests.findByIdAndSandboxId(id, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        ChangeRequestEntity request = proposed(found.get());
        request.reject(clock.instant());
        audit.append(AuditAction.CHANGE_REJECTED, sandboxId.toString(), request.getBaseVersionId(), id,
                JSON.createObjectNode().put("requestText", request.getRequestText()).put("note", note));
        return Optional.of(ChangeDecision.of(request, null));
    }

    private static ChangeRequestEntity proposed(ChangeRequestEntity request) {
        if (!request.isProposed()) {
            throw new VersionStatusException("the change request is " + request.getStatus());
        }
        return request;
    }

    /**
     * The row Document 2 describes: {@code patches_json} the patches as validated with the request's id in every
     * pending provenance, {@code rationale_json} the summary, the untouched rules, the notes and the candidates.
     */
    private ChangeRequestView store(ChangeBase base, String request, Proposal proposal, StructuralDiff diff,
            Regression regression, UUID sandboxId) {
        UUID id = UUID.randomUUID();
        ObjectNode stored = proposal.storedAs(id.toString());
        ArrayNode patches = (ArrayNode) stored.required("patches");
        ObjectNode rationale = JSON.createObjectNode();
        rationale.put("summary", stored.required("summary").asString());
        rationale.set("untouched", stored.required("untouched"));
        rationale.put("notes", stored.required("notes").asString());
        rationale.set("candidates", JSON.valueToTree(proposal.candidates()));
        String actor = sandboxId.toString();
        ChangeRequestEntity entity = requests.save(new ChangeRequestEntity(id, sandboxId, base.versionId(), request,
                patches.toString(), rationale.toString(), JSON.valueToTree(regression).toString(), clock.instant(),
                actor));
        ObjectNode details = JSON.createObjectNode()
                .put("rulesetId", base.rulesetId().toString())
                .put("versionNo", base.versionNo())
                .put("patches", patches.size());
        audit.append(AuditAction.CHANGE_PROPOSED, actor, base.versionId(), id, details);
        return new ChangeRequestView(entity.getId(), entity.getBaseVersionId(), entity.getRequestText(),
                entity.getStatus(), stored, proposal.candidates(), diff, regression, entity.getCreatedAt(),
                entity.getActor());
    }
}
