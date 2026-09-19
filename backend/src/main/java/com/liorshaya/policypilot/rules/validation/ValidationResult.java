package com.liorshaya.policypilot.rules.validation;

import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The findings of one validation, in layer order, and the mapped rule set; {@code ruleSet} is {@code null} when
 * the document failed the schema and so was never mapped.
 */
public record ValidationResult(List<Finding> findings, @Nullable RuleSet ruleSet) {

    public ValidationResult {
        findings = List.copyOf(findings);
    }

    /** Whether any finding is an error, which blocks publishing (Document 3, Publishing gate). */
    public boolean hasErrors() {
        return findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
    }
}
