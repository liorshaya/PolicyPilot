/**
 * Rule set JSON: parsing with the Document 5 limits, and the strict, lossless mapping between the JSON tree and
 * the records of {@code rules.model}. The mapping is written by hand over the tree, so there is no polymorphic
 * type handling and no reflection; the JSON Schema is checked on the tree before any mapping
 * ({@code rules.validation}).
 */
@NullMarked
package com.liorshaya.policypilot.rules.json;

import org.jspecify.annotations.NullMarked;
