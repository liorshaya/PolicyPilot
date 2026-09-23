package com.liorshaya.policypilot.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * CSV as the exports write it (Document 5, CSV and formula injection: "Cells starting with =, +, -, @ are prefixed
 * with a single quote"; RFC 4180 for quoting; a byte order mark so spreadsheets read Hebrew as UTF-8).
 */
class CsvTest {

    // Document 5. Expected: each of the four characters that start a formula prefixed with a quote
    @ParameterizedTest
    @ValueSource(strings = {"=HYPERLINK(1)", "+1", "-1", "@SUM(A1)"})
    void aCellThatReadsAsAFormulaIsPrefixedWithAQuote(String value) {
        assertThat(Csv.cell(value)).isEqualTo("'" + value);
    }

    // Expected: a cell that does not start with one of the four, an empty cell, and Hebrew, unchanged
    @Test
    void otherCellsAreWrittenUnchanged() {
        assertThat(Csv.cell("a=1")).isEqualTo("a=1");
        assertThat(Csv.cell("")).isEqualTo("");
        assertThat(Csv.cell("דחייה")).isEqualTo("דחייה");
    }

    // RFC 4180. Expected: a quote, a comma, a line feed or a carriage return quote the cell, a quote doubled
    @Test
    void quotesCommasAndLineBreaksQuoteTheCell() {
        assertThat(Csv.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(Csv.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(Csv.cell("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(Csv.cell("a\rb")).isEqualTo("\"a\rb\"");
    }

    // Expected: a formula that also needs quoting is prefixed first, then quoted
    @Test
    void aFormulaWithACommaIsPrefixedThenQuoted() {
        assertThat(Csv.cell("=A1,B1")).isEqualTo("\"'=A1,B1\"");
    }

    // Expected: the byte order mark, the header and each row, cells joined by commas and each row ended by CRLF
    @Test
    void aDocumentIsTheMarkTheHeaderAndTheRows() {
        String csv = Csv.document(List.of("a", "b"), List.of(List.of("1", "2"), List.of("3", "=4")));

        assertThat(csv).isEqualTo(Csv.BYTE_ORDER_MARK + "a,b\r\n1,2\r\n3,'=4\r\n");
        assertThat(Csv.BYTE_ORDER_MARK).isEqualTo(Character.toString(0xFEFF));
    }
}
