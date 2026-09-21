package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.support.Requirement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The citation protocol of Document 4 (Prompt 4, Marker resolution; Output Contracts, Citation marker protocol):
 * markers are {@code [[p:<index>]]}, {@code [[r:<ruleId>]]}, {@code [[d:<applicationNumber>]]} and {@code
 * [[sim:<id>]]}; one whose id was supplied this turn stays in the text and is cited once; any other marker, unknown
 * or malformed, is removed from what is shown and counted. Markers arrive split across tokens, so the resolver holds
 * back what could still become one.
 */
@Requirement("FR-13")
class MarkerResolverTest {

    private final Set<String> supplied = new HashSet<>(Set.of("p:2", "p:7", "r:R-330", "d:17"));
    private final MarkerResolver resolver = new MarkerResolver(supplied::contains);

    // Expected: the text as written, the marker kept in place, and p:2 cited
    @Test
    void aSuppliedMarkerStaysInTheTextAndIsCited() {
        String shown = resolver.accept("The term is 84 months.[[p:2]] ") + resolver.finish();

        assertThat(shown).isEqualTo("The term is 84 months.[[p:2]] ");
        assertThat(resolver.cited()).containsExactly("p:2");
        assertThat(resolver.dropped()).isZero();
    }

    // Document 4: "markers are parsed as they arrive". Expected: nothing of a half marker is shown until it closes,
    // then the whole marker
    @Test
    void aMarkerSplitAcrossTokensIsHeldUntilItCloses() {
        String first = resolver.accept("Referred.[[r:R-");
        String second = resolver.accept("330]] Done.");

        assertThat(first).isEqualTo("Referred.");
        assertThat(second).isEqualTo("[[r:R-330]] Done.");
        assertThat(resolver.cited()).containsExactly("r:R-330");
    }

    // Document 4: "a marker whose id was not supplied in this turn's context or tool results is removed from the
    // displayed text and counted". Expected: the sentence without [[p:5]], one dropped
    @Test
    void anUnsuppliedMarkerIsRemovedAndCounted() {
        String shown = resolver.accept("Approved.[[p:5]] Next.") + resolver.finish();

        assertThat(shown).isEqualTo("Approved. Next.");
        assertThat(resolver.cited()).isEmpty();
        assertThat(resolver.dropped()).isEqualTo(1);
    }

    // Document 4, Citation marker protocol: the four kinds and their ids. Expected: each malformed marker removed
    // and counted, the text around it kept
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"[[p:]]", "[[x:1]]", "[[p:seven]]", "[[r:320]]", "[[d:-4]]", "[[sim:]]", "[[p:7 ]]"})
    void aMalformedMarkerIsRemovedAndCounted(String marker) {
        String shown = resolver.accept("A" + marker + "B") + resolver.finish();

        assertThat(shown).isEqualTo("AB");
        assertThat(resolver.dropped()).isEqualTo(1);
    }

    // Document 4, Citation marker protocol: the grammar holds on its own, even if the ids offered were wrong.
    // Expected: with every id accepted, the malformed markers are still removed and the well-formed ones kept
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"[[r:320]]", "[[r:R320]]", "[[p:0]]", "[[d:017]]", "[[sim:17:x=1]]", "[[sim:d17:X=1]]"})
    void theGrammarHoldsEvenWhenEveryIdIsAccepted(String marker) {
        MarkerResolver acceptingAll = new MarkerResolver(id -> true);

        String shown = acceptingAll.accept("A" + marker + "B[[r:R-320]]") + acceptingAll.finish();

        assertThat(shown).isEqualTo("AB[[r:R-320]]");
        assertThat(acceptingAll.cited()).containsExactly("r:R-320");
    }

    // Expected: a marker still open when the answer ends is never shown
    @Test
    void aMarkerLeftOpenAtTheEndIsDropped() {
        String shown = resolver.accept("Referred. [[p:7") + resolver.finish();

        assertThat(shown).isEqualTo("Referred. ");
        assertThat(resolver.dropped()).isEqualTo(1);
    }

    // Document 4: "any number per sentence"; the citations event lists each source once. Expected: both markers in the
    // text, p:7 cited once
    @Test
    void aRepeatedMarkerIsShownEachTimeAndCitedOnce() {
        String shown = resolver.accept("One.[[p:7]] Two.[[p:7]][[d:17]]") + resolver.finish();

        assertThat(shown).isEqualTo("One.[[p:7]] Two.[[p:7]][[d:17]]");
        assertThat(resolver.cited()).containsExactly("p:7", "d:17");
    }

    // Expected: brackets that cannot become a marker are ordinary text and are never held back
    @Test
    void bracketsThatCannotBeAMarkerStayText() {
        assertThat(resolver.accept("a [b] c [[ d")).isEqualTo("a [b] c [[ d");
        assertThat(resolver.accept(" [x] [")).isEqualTo(" [x] ");
        assertThat(resolver.finish()).isEqualTo("[");
    }

    // Document 4: ids come from "context chunks or tool results" of the same turn, and a tool may run mid-answer.
    // Expected: a simulation id supplied after the resolver was made is accepted
    @Test
    void anIdAToolSuppliesDuringTheTurnIsAccepted() {
        supplied.add("sim:d17:has_guarantor=true");

        String shown = resolver.accept("Approved.[[sim:d17:has_guarantor=true]]") + resolver.finish();

        assertThat(shown).isEqualTo("Approved.[[sim:d17:has_guarantor=true]]");
        assertThat(resolver.cited()).containsExactly("sim:d17:has_guarantor=true");
    }

    // Expected: text streamed character by character resolves exactly as the whole text does
    @Test
    void oneCharacterAtATimeGivesTheSameResult() {
        String answer = "Referred by R-330.[[r:R-330]] Not [[p:9]] this.[[p:7]]";
        StringBuilder shown = new StringBuilder();
        answer.codePoints().forEach(c -> shown.append(resolver.accept(Character.toString(c))));
        shown.append(resolver.finish());

        assertThat(shown.toString()).isEqualTo("Referred by R-330.[[r:R-330]] Not  this.[[p:7]]");
        assertThat(resolver.cited()).isEqualTo(List.of("r:R-330", "p:7"));
        assertThat(resolver.dropped()).isEqualTo(1);
    }
}
