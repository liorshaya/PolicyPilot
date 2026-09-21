package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rules.model.Language;
import org.junit.jupiter.api.Test;

/** Document 4, Prompt 4: the sentence that ends a turn past the tool caps, word for word in both languages. */
class FixedSentencesTest {

    @Test
    void theToolLimitSentenceIsDocumentFoursInBothLanguages() {
        assertThat(FixedSentences.toolLimit()).containsEntry(Language.EN,
                "This question needs more lookups than one answer may make; ask about one application or one change at "
                        + "a time.")
                .containsEntry(Language.HE,
                        "השאלה דורשת יותר בדיקות ממה שתשובה אחת רשאית לבצע; "
                                + "אפשר לשאול על בקשה אחת או על שינוי אחד בכל פעם.");
    }
}
