package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.Rule;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns chunk ids into citations against the version itself (Document 5, Vector and embedding weaknesses: "a chunk's
 * provenance (paragraph index or rule id) is verified when a citation is resolved"). An id the version cannot vouch
 * for, a rule it does not have, a paragraph past its last, an id of another kind, is dropped rather than cited.
 */
public final class CitationBuilder {

    private final Map<String, Rule> rules;
    private final Set<Integer> paragraphs;

    public CitationBuilder(RuleSet ruleSet, List<PolicyVersionRef.Paragraph> paragraphs) {
        this.rules = ruleSet.rules().stream().collect(Collectors.toMap(Rule::id, Function.identity()));
        this.paragraphs = paragraphs.stream().map(PolicyVersionRef.Paragraph::index).collect(Collectors.toSet());
    }

    public List<Citation> cite(List<String> ids) {
        return ids.stream().map(this::citation).flatMap(Optional::stream).toList();
    }

    private Optional<Citation> citation(String id) {
        if (id.startsWith(Chunk.Kind.PARAGRAPH.prefix() + ":")) {
            return paragraphIndex(id.substring(2))
                    .filter(paragraphs::contains)
                    .map(index -> new Citation(id, Citation.Kind.PARAGRAPH, index, null, null));
        }
        if (id.startsWith(Chunk.Kind.RULE.prefix() + ":")) {
            return Optional.ofNullable(rules.get(id.substring(2))).map(rule -> new Citation(id, Citation.Kind.RULE,
                    rule.provenance() instanceof Provenance.Quoted quoted ? quoted.paragraph() : null, rule.id(),
                    rule.label()));
        }
        return Optional.empty();
    }

    private static Optional<Integer> paragraphIndex(String text) {
        try {
            return Optional.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
