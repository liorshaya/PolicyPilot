package com.liorshaya.policypilot.ai.service;

import java.util.List;

/**
 * What candidate selection found for a change request (Document 4, Prompt 5): the rules it started from, the rules the
 * model will be shown, in evaluation order, and the request's fields.
 *
 * @param seeds the rules closest to the request and the rules it names by id
 * @param ruleIds the candidates: the only rules a proposal may replace or remove (Document 3, Patch validation)
 * @param fields the fields the seeds' conditions test and the request names
 */
public record Candidates(List<String> seeds, List<String> ruleIds, List<String> fields) {

    public Candidates {
        seeds = List.copyOf(seeds);
        ruleIds = List.copyOf(ruleIds);
        fields = List.copyOf(fields);
    }
}
