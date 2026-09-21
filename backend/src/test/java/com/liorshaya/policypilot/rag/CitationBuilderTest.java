package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import com.liorshaya.policypilot.rag.service.Citation;
import com.liorshaya.policypilot.rag.service.CitationBuilder;
import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.rules.model.RuleSet;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Citation building (Document 2, RAG pipeline, Prompting: "the API resolves ids to paragraph or rule links"; Document
 * 5, Vector and embedding weaknesses: "a chunk's provenance (paragraph index or rule id) is verified when a citation is
 * resolved"). Expected values are the lending fixture's paragraphs, rules, labels and provenance.
 */
class CitationBuilderTest {

    private static final RuleSet LENDING = new RuleSetMapper().toRuleSet(Fixtures.lendingV1());

    private final CitationBuilder citations = new CitationBuilder(LENDING, paragraphs());

    // Expected: p:7 cites paragraph 7 and no rule
    @Test
    void aParagraphChunkCitesItsParagraph() {
        assertThat(citations.cite(List.of("p:7")))
                .containsExactly(new Citation("p:7", Citation.Kind.PARAGRAPH, 7, null, null));
    }

    // Expected: R-330's label from ruleset.v1.json, and paragraph 7, which its provenance quotes
    @Test
    void aQuotedRuleCitesTheRuleAndTheParagraphItQuotes() {
        assertThat(citations.cite(List.of("r:R-330"))).containsExactly(new Citation("r:R-330", Citation.Kind.RULE, 7,
                "R-330", "בדיקת חתם: אירוע אשראי אחד ללא ערב"));
    }

    // Expected: R-310's provenance is an analyst's, so it cites no paragraph
    @Test
    void anAnalystsRuleCitesNoParagraph() {
        assertThat(citations.cite(List.of("r:R-310"))).containsExactly(new Citation("r:R-310", Citation.Kind.RULE, null,
                "R-310", "בדיקה ידנית: ותק לא דווח"));
    }

    // Document 5: provenance verified at citation time. Expected: a rule the version does not have, a paragraph past its
    // ninth and an id of no known kind are all dropped, and the rest keep their order
    @Test
    void anIdTheVersionCannotVouchForIsDropped() {
        assertThat(citations.cite(List.of("r:R-999", "p:2", "p:10", "d:17", "r:R-130")))
                .extracting(Citation::id).containsExactly("p:2", "r:R-130");
    }

    private static List<PolicyVersionRef.Paragraph> paragraphs() {
        List<String> texts = Fixtures.lendingParagraphs();
        List<PolicyVersionRef.Paragraph> paragraphs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            paragraphs.add(new PolicyVersionRef.Paragraph(UUID.randomUUID(), i + 1, texts.get(i)));
        }
        return paragraphs;
    }
}
