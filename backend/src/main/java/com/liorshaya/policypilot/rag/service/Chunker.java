package com.liorshaya.policypilot.rag.service;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rules.model.RuleSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Splits a published version into its retrieval corpus (Document 4, Retrieval Pipeline, Corpus): one chunk per
 * policy paragraph, whose text is the paragraph as stored, then one per rule, rendered by {@link RuleText}.
 * Paragraphs are already the provenance unit, so nothing is split by size (Document 2, NFR-5: never byte-based).
 */
public class Chunker {

    public List<Chunk> chunk(List<PolicyVersionRef.Paragraph> paragraphs, RuleSet ruleSet) {
        List<Chunk> chunks = new ArrayList<>();
        for (PolicyVersionRef.Paragraph paragraph : paragraphs) {
            chunks.add(new Chunk(Chunk.Kind.PARAGRAPH, Integer.toString(paragraph.index()), paragraph.text()));
        }
        RuleText text = new RuleText(ruleSet.fields());
        ruleSet.rules().forEach(rule -> chunks.add(new Chunk(Chunk.Kind.RULE, rule.id(), text.render(rule))));
        return List.copyOf(chunks);
    }
}
