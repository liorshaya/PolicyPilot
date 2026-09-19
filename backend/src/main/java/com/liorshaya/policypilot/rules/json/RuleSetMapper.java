package com.liorshaya.policypilot.rules.json;

import com.liorshaya.policypilot.rules.model.Provenance;
import com.liorshaya.policypilot.rules.model.RuleSet;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Reads rule set JSON and maps it to and from the DSL records.
 *
 * <p>Order of use (Document 5, JSON and deserialization): {@link #readTree} applies the size and nesting limits,
 * the caller validates the tree against the JSON Schema, and only then {@link #toRuleSet} maps it. The mapping is
 * strict on its own as well: an unknown property or a wrong shape is a {@link RuleSetFormatException}.
 */
public final class RuleSetMapper {

    /** Document 5: input size limit of 1 MB, counted in characters of the JSON text. */
    public static final int MAX_DOCUMENT_CHARS = 1024 * 1024;

    /** Document 5: nesting depth limit of 32. */
    public static final int MAX_NESTING_DEPTH = 32;

    private final JsonMapper mapper = JsonMapper.builder(JsonFactory.builder()
                    .streamReadConstraints(StreamReadConstraints.builder()
                            .maxDocumentLength(MAX_DOCUMENT_CHARS)
                            .maxNestingDepth(MAX_NESTING_DEPTH)
                            .build())
                    .build())
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    /** Parses JSON text into a tree; throws {@link RuleSetFormatException} on bad syntax or a broken limit. */
    public JsonNode readTree(String json) {
        try {
            return mapper.readTree(json);
        } catch (StreamConstraintsException e) {
            throw new RuleSetFormatException("", e.getOriginalMessage());
        } catch (JacksonException e) {
            throw new RuleSetFormatException("", "the document is not well-formed JSON");
        }
    }

    /** Maps a tree to the records; throws {@link RuleSetFormatException} with the pointer of the first problem. */
    public RuleSet toRuleSet(JsonNode document) {
        return RuleSetReader.ruleSet(document);
    }

    /** Writes the records back as a tree; omitted optional attributes stay omitted. */
    public ObjectNode toJson(RuleSet ruleSet) {
        return new RuleSetWriter(mapper.getNodeFactory()).ruleSet(ruleSet);
    }

    /** Writes one provenance as the DSL writes it; the trace copies each rule's provenance (Document 3). */
    public ObjectNode toJson(Provenance provenance) {
        return new RuleSetWriter(mapper.getNodeFactory()).provenance(provenance);
    }
}
