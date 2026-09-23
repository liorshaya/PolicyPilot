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
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Change requests up to the proposal (Document 2, Flow 4; Work Plan day 12): the impact analysis, the model's proposal
 * and its validation, and, only for a proposal that validated, the stored PROPOSED request with its CHANGE_PROPOSED
 * audit entry in one transaction. A proposal that fails Patch validation after its repairs or is refused by the
 * proposal validator is never stored (Document 3, Patch validation).
 *
 * <p>The actor is the sandbox (Document 5, Why no user accounts). The model is called outside any transaction, so a
 * slow provider holds no connection.
 */
@Service
public class ChangeRequestService {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ChangeAnalysis analysis;
    private final ChangeService proposer;
    private final ChangeRequestRepository requests;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ChangeRequestService(ChangeAnalysis analysis, ChangeService proposer, ChangeRequestRepository requests,
            AuditLog audit, TransactionTemplate transactions, Clock clock) {
        this.analysis = analysis;
        this.proposer = proposer;
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
     * Analyzes the request, asks for a proposal and stores it when it validates.
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
        return new Submitted.Stored(Objects.requireNonNull(
                transactions.execute(status -> store(base, request, proposal, sandboxId))));
    }

    /**
     * The row Document 2 describes: {@code patches_json} the patches as validated with the request's id in every
     * pending provenance, {@code rationale_json} the summary, the untouched rules, the notes and the candidates.
     */
    private ChangeRequestView store(ChangeBase base, String request, Proposal proposal, UUID sandboxId) {
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
                patches.toString(), rationale.toString(), clock.instant(), actor));
        ObjectNode details = JSON.createObjectNode()
                .put("rulesetId", base.rulesetId().toString())
                .put("versionNo", base.versionNo())
                .put("patches", patches.size());
        audit.append(AuditAction.CHANGE_PROPOSED, actor, base.versionId(), id, details);
        return new ChangeRequestView(entity.getId(), entity.getBaseVersionId(), entity.getRequestText(),
                entity.getStatus(), stored, proposal.candidates(), entity.getCreatedAt(), entity.getActor());
    }
}
