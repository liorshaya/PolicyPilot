package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.rag.service.LexicalText;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The one lexical normalization chunks and questions go through before PostgreSQL's {@code simple} parser sees them
 * (Document 4, Retrieval Pipeline, Lexical index). Without it the parser reads {@code R-320} as {@code r} and
 * {@code -320} and {@code 8,000} as {@code 8} and {@code 000}, measured on {@code pgvector/pgvector:pg16}.
 */
class LexicalTextTest {

    // Document 4, Lexical index: each spelling the document names, and the text it leaves alone
    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', textBlock = """
            rule id with its hyphen                 | מה עושה הכלל R-320?         | מה עושה הכלל r320?
            rule id without its hyphen              | what does R320 do           | what does r320 do
            rule id in lower case                   | r-320                       | r320
            rule id of two and of four digits       | R-10, R-1000                | r10, r1000
            grouped number                          | לא תפחת מ-8,000 ש"ח          | לא תפחת מ 8000 ש"ח
            grouped number of two groups            | 1,500,000                   | 1500000
            a comma that does not group             | 1,5 and 12,34               | 1,5 and 12,34
            hyphen between a letter and a digit     | ל-84 חודשים                 | ל 84 חודשים
            Hebrew words stay as they are           | תקופת ההחזר המקסימלית       | תקופת ההחזר המקסימלית
            a letter before the R makes no rule id  | ERR-320                     | ERR 320
            one digit is no rule id                 | R-9                         | R 9
            """)
    void normalizesTheSpellingsDocumentFourNames(String name, String text, String expected) {
        assertThat(LexicalText.normalize(text)).isEqualTo(expected);
    }
}
