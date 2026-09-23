package com.liorshaya.policypilot.common;

import java.util.List;

/**
 * CSV as the exports write it (Document 2, export; Document 5, CSV and formula injection): a cell that starts with
 * {@code =}, {@code +}, {@code -} or {@code @} is prefixed with a single quote, so a spreadsheet reads it as text
 * and never as a formula; cells are quoted per RFC 4180, rows end in CRLF, and a document starts with a byte order
 * mark so spreadsheets read Hebrew as UTF-8.
 */
public final class Csv {

    /** The byte order mark that makes spreadsheet programs read the file as UTF-8. */
    public static final String BYTE_ORDER_MARK = "\uFEFF";

    private static final String FORMULA_CHARACTERS = "=+-@";

    private Csv() {}

    /** A document: the byte order mark, the header, then one row per list of cells. */
    public static String document(List<String> header, List<List<String>> rows) {
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK).append(row(header));
        rows.forEach(cells -> csv.append(row(cells)));
        return csv.toString();
    }

    /** One row: its cells written and joined by commas, ending in CRLF. */
    public static String row(List<String> cells) {
        return String.join(",", cells.stream().map(Csv::cell).toList()) + "\r\n";
    }

    /** One cell as it is written: formula-prefixed when needed, then quoted per RFC 4180. */
    static String cell(String value) {
        String prefixed = !value.isEmpty() && FORMULA_CHARACTERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        if (prefixed.contains("\"") || prefixed.contains(",") || prefixed.contains("\n") || prefixed.contains("\r")) {
            return '"' + prefixed.replace("\"", "\"\"") + '"';
        }
        return prefixed;
    }
}
