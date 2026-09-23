package com.liorshaya.policypilot.ai.change;

import com.liorshaya.policypilot.ai.service.Candidates;
import com.liorshaya.policypilot.ai.service.ChangeBase;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.rag.service.RetrievalService;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.patch.Impact;
import com.liorshaya.policypilot.rules.patch.Mentions;
import com.liorshaya.policypilot.ruleset.service.EmbeddingSource;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatus;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The impact analysis of a change request (Document 2, Change impact analysis; Document 4, Prompt 5, Candidate
 * selection): the published version it is proposed against, and the candidate rules the model will be shown. Nothing
 * here calls a model: the request is embedded, the version's rules nearest it are the seeds with those it names, and
 * the rule set's own field references decide the rest.
 */
@Service
public class ChangeAnalysis {

    /** Document 4, Candidate selection: "the two rule chunks of the version most similar to it are the seeds". */
    static final int SEEDS = 2;

    private final RulesetService rulesets;
    private final PolicyService policies;
    private final RetrievalService retrieval;

    public ChangeAnalysis(RulesetService rulesets, PolicyService policies, RetrievalService retrieval) {
        this.rulesets = rulesets;
        this.policies = policies;
        this.retrieval = retrieval;
    }

    /**
     * The version a change is proposed against: empty when the sandbox cannot see it, and a status conflict when it is
     * not PUBLISHED or its embedding is not READY, as candidate selection reads its chunks (Document 2, API Surface).
     */
    public Optional<ChangeBase> base(UUID rulesetId, int versionNo, UUID sandboxId) {
        Optional<VersionView> found = rulesets.version(rulesetId, versionNo, sandboxId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        VersionView version = found.get();
        if (version.status() != VersionStatus.PUBLISHED) {
            throw new VersionStatusException("version " + versionNo + " is " + version.status()
                    + "; a change is proposed against a published version");
        }
        EmbeddingSource corpus = rulesets.corpus(rulesetId, versionNo, sandboxId).orElseThrow();
        PolicyVersionRef policy = policies.version(version.policyVersionId()).orElseThrow();
        String title = policies.find(policy.documentId(), sandboxId).map(PolicyView::title).orElseThrow();
        return Optional.of(new ChangeBase(rulesetId, versionNo, version.document(), corpus, title,
                Set.copyOf(rulesets.retiredIds(rulesetId, sandboxId))));
    }

    /** The candidates of a request on a version (Document 4, Prompt 5). */
    public Candidates candidates(ChangeBase base, String request) {
        Mentions mentions = Mentions.of(request);
        Set<String> seeds = new LinkedHashSet<>(retrieval.rulesNearest(base.corpus(), request, SEEDS));
        base.ruleSet().rules().stream().map(Rule::id).filter(mentions.ruleIds()::contains).forEach(seeds::add);
        Impact impact = Impact.of(base.ruleSet(), seeds, mentions.fields(base.ruleSet()));
        return new Candidates(List.copyOf(seeds), impact.ruleIds(), impact.fields());
    }
}
