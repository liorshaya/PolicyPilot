package com.liorshaya.policypilot.ruleset.service;

import com.liorshaya.policypilot.engine.CompiledRuleSet;
import java.util.UUID;

/**
 * A published version ready to decide: its row id, the rule set it belongs to and the compiled document the engine
 * evaluates (Document 2, Flow 2: "the compiled rule set is cached per version in memory").
 */
public record PublishedVersion(UUID versionId, UUID rulesetId, String domain, int versionNo,
        CompiledRuleSet compiled) {}
