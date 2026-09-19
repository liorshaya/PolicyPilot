package com.liorshaya.policypilot.rules.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Quote normalization (Document 3, Provenance; Document 5, bidi and invisible characters). Every expected string is
 * what {@code norm} in {@code fixtures/reference/reference_check.py} returns for the same input.
 */
@Requirement({"FR-4", "NFR-5"})
@Isolated("one test switches the JVM default locale")
class QuoteNormalizerTest {

    private static final List<String> LENDING_PARAGRAPHS = Fixtures.lendingParagraphs();

    @Test
    void lowerCasesWithoutTheDefaultLocale() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(QuoteNormalizer.normalize("APPLICANTS MUST BE AT LEAST 21 YEARS OLD, İstanbul"))
                    .isEqualTo("applicants must be at least 21 years old istanbul");
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void collapsesWhitespaceRunsIncludingNoBreakSpace() {
        assertThat(QuoteNormalizer.normalize("a\t b  c\n\nd  ")).isEqualTo("a b c d");
    }

    @Test
    void stripsLatinPunctuationAndQuotationMarks() {
        assertThat(QuoteNormalizer.normalize(
                "\"Quoted,\" (he said); [x] {y}: ok! why? a-b–c—d / 40% ‘single’ “double” «guillemets»"))
                .isEqualTo("quoted he said x y ok why abcd 40 single double guillemets");
    }

    @Test
    void stripsHebrewPunctuation() {
        // gershayim, maqaf, geresh, paseq, sof pasuq, nun hafukha
        assertThat(QuoteNormalizer.normalize("ש״ח מ־1,000 בגרש׳ ׀ פסוק׃ נ׆")).isEqualTo("שח מ1000 בגרש פסוק נ");
    }

    @Test
    void removesNiqqud() {
        assertThat(QuoteNormalizer.normalize("גִּילוֹ שֶׁל הַמְּבַקֵּשׁ")).isEqualTo("גילו של המבקש");
    }

    @Test
    void appliesCompatibilityDecomposition() {
        assertThat(QuoteNormalizer.normalize("ＡＢＣ ﬁle ①")).isEqualTo("abc file 1");
    }

    @Test
    void removesBidiControlCharacters() {
        assertThat(QuoteNormalizer.normalize(
                "a\u202Ab\u202Bc\u202Cd\u202De\u202Ef\u2066g\u2067h\u2068i\u2069j")).isEqualTo("abcdefghij");
        // Document 5, RT-09: an override hiding one word inside another no longer changes what is compared
        assertThat(QuoteNormalizer.occursIn("גילו\u200B 21 עד\u202E 70", LENDING_PARAGRAPHS.getFirst())).isTrue();
    }

    @Test
    void removesZeroWidthCharacters() {
        assertThat(QuoteNormalizer.normalize("מא\u200Bשר\u200C ד\u200Dחה\uFEFF")).isEqualTo("מאשר דחה");
    }

    @Test
    void lendingQuoteWithoutItsCommasMatchesParagraphTwo() {
        assertThat(QuoteNormalizer.occursIn("סכום ההלוואה יהיה בין 10000 ל150000 ש״ח", LENDING_PARAGRAPHS.get(1)))
                .isTrue();
        assertThat(QuoteNormalizer.occursIn("גִּילוֹ 21 עד 70", LENDING_PARAGRAPHS.getFirst())).isTrue();
    }

    @Test
    void paraphraseDoesNotMatch() {
        assertThat(QuoteNormalizer.occursIn("גיל 21 עד 70", LENDING_PARAGRAPHS.getFirst())).isFalse();
    }

    @Test
    void quoteShorterThanThreeCharactersAfterNormalizationNeverMatches() {
        String paragraph = LENDING_PARAGRAPHS.getFirst();

        assertThat(QuoteNormalizer.occursIn("...", paragraph)).isFalse();
        assertThat(QuoteNormalizer.occursIn("21.", paragraph)).isFalse();
        assertThat(QuoteNormalizer.occursIn("ד 7", paragraph)).isTrue();
        assertThat(QuoteNormalizer.occursIn("עד 7", paragraph)).isTrue();
    }
}
