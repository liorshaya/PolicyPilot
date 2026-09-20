package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.ruleset.service.RulesetView;
import java.util.List;
import java.util.UUID;

/**
 * The rule sets a sandbox can see (Document 2, API Surface: {@code GET /rulesets}), each with its policy and the
 * number and status of every version, so the web app can find the seeded rule set.
 */
public record RulesetsResponse(@JsonProperty(required = true) List<Ruleset> rulesets) {

    /** One rule set; {@code forkedFromId} appears only on a sandbox's copy of a protected rule set. */
    public record Ruleset(
            @JsonProperty(required = true) UUID id,
            @JsonProperty(required = true) String name,
            @JsonProperty(required = true) String domain,
            @JsonProperty(value = "protected", required = true) boolean isProtected,
            @JsonInclude(JsonInclude.Include.NON_NULL) UUID forkedFromId,
            @JsonProperty(required = true) UUID policyId,
            @JsonProperty(required = true) List<RulesetVersion> versions) {}

    /** One version of a rule set: its number and its status. */
    public record RulesetVersion(@JsonProperty(required = true) int versionNo,
            @JsonProperty(required = true) String status) {}

    public static RulesetsResponse of(List<RulesetView> rulesets) {
        return new RulesetsResponse(rulesets.stream()
                .map(ruleset -> new Ruleset(ruleset.id(), ruleset.name(), ruleset.domain(), ruleset.isProtected(),
                        ruleset.forkedFromId(), ruleset.policyId(), ruleset.versions().stream()
                                .map(version -> new RulesetVersion(version.versionNo(), version.status().name()))
                                .toList()))
                .toList());
    }
}
