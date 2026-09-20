package com.liorshaya.policypilot.web.request;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /policies/{id}/rulesets}: nothing, or the analyst's hints for the author prompt
 * (Document 4, Prompt 1: "optional domain hints from the analyst"). Hints are capped like every other free text
 * a visitor sends (Document 5, Input limits).
 */
public record GenerateRulesRequest(@Size(max = 2048) @Nullable String hints) {}
