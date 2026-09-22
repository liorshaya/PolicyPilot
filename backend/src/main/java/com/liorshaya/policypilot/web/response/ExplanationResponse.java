package com.liorshaya.policypilot.web.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.liorshaya.policypilot.ai.service.ExplainService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The explanation of one decision (Document 4, Explanation contract), after every entry the trace does not support was
 * dropped: the summary, the rules that fired with their paragraphs, the flags a person still checks, and at most
 * three rules that were evaluated and did not fire.
 */
public record ExplanationResponse(
        @JsonProperty(required = true) UUID decisionId,
        @JsonProperty(required = true) @Schema(allowableValues = {"officer", "applicant"}) String audience,
        @JsonProperty(required = true) @Schema(allowableValues = {"he", "en"}) String language,
        @JsonProperty(required = true) String promptVersion,
        @JsonProperty(required = true) String summary,
        @JsonProperty(required = true) List<FactorResponse> factors,
        @JsonProperty(required = true) List<ConditionResponse> conditions,
        @JsonProperty(required = true) List<NotAppliedResponse> notApplied) {

    /** A rule that fired; {@code paragraph} is null for a rule an analyst added. */
    public record FactorResponse(
            @JsonProperty(required = true) String ruleId,
            @JsonProperty(required = true) @Schema(nullable = true) Integer paragraph,
            @JsonProperty(required = true) String statement) {}

    public record ConditionResponse(
            @JsonProperty(required = true) String flagCode,
            @JsonProperty(required = true) String statement) {}

    public record NotAppliedResponse(
            @JsonProperty(required = true) String ruleId,
            @JsonProperty(required = true) String statement) {}

    public static ExplanationResponse of(UUID decisionId, ExplainService.Audience audience,
            ExplainService.Explained explained) {
        ExplainService.Explanation explanation = explained.explanation();
        return new ExplanationResponse(decisionId, audience.json(), explanation.language(), explained.promptVersion(),
                explanation.summary(),
                explanation.factors().stream()
                        .map(factor -> new FactorResponse(factor.ruleId(), factor.paragraph(), factor.statement()))
                        .toList(),
                explanation.conditions().stream()
                        .map(condition -> new ConditionResponse(condition.flagCode(), condition.statement()))
                        .toList(),
                explanation.notApplied().stream()
                        .map(rule -> new NotAppliedResponse(rule.ruleId(), rule.statement()))
                        .toList());
    }
}
