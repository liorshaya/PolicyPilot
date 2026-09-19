package com.liorshaya.policypilot.web.error;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry of the envelope's details: the JSON pointer of the offending node and what is wrong with it, never the
 * offending value (Document 5, Error responses).
 */
public record ErrorDetail(@JsonProperty(required = true) String path, @JsonProperty(required = true) String problem) {}
