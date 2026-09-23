package com.liorshaya.policypilot.web.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * The file exports of the API (Document 2, export: JSON or CSV by the Accept header; Document 5, served as an
 * attachment). A file name is built from route constants and parsed ids, never from text the caller sent.
 */
final class Exports {

    /** The media type of a CSV export beside JSON. */
    static final String TEXT_CSV = "text/csv";

    private Exports() {}

    /** Whether the Accept header asks for CSV rather than JSON. */
    static boolean wantsCsv(@Nullable String accept) {
        return accept != null && accept.contains(TEXT_CSV);
    }

    /** The body as an attachment named {@code <name>.csv} or {@code <name>.json}. */
    static ResponseEntity<Object> attachment(String name, boolean csv, Object body) {
        return ResponseEntity.ok()
                .contentType(csv ? MediaType.valueOf(TEXT_CSV + ";charset=UTF-8") : MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + name + (csv ? ".csv\"" : ".json\""))
                .body(body);
    }
}
