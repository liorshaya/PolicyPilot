/**
 * The Rules DSL 1.0 (Document 3), in three parts: {@code rules.model}, the records ({@code RuleSet}, {@code Rule},
 * {@code Condition}, {@code Action}, {@code Provenance}); {@code rules.json}, parsing with the Document 5 limits
 * and the strict mapping between JSON and the records; {@code rules.validation}, the JSON Schema and the validator
 * with its four contexts (AUTHORING, CHANGE_PROPOSAL, PUBLISH, ANALYST_EDIT) and every static check code.
 *
 * <p>Allowed dependencies: the JDK, Jackson, the JSON Schema validator and RE2J. Nothing else: no Spring, no
 * {@code config}, no {@code common}. Coverage gate: 100% line, 95% branch, PIT at least 90% (Document 6).
 * Built on day 2.
 */
package com.liorshaya.policypilot.rules;
