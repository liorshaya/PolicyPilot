/**
 * The deterministic rules engine (Document 2, Rules Engine Design; Document 3, Evaluation Semantics):
 * {@code RuleEngine}, {@code CompiledRuleSet}, the trace, operators and combinators, simulation as a pure
 * re-evaluation.
 *
 * <p>Allowed dependencies: the JDK, Jackson, RE2J and {@code rules}. No clock, no random source, no I/O, no model
 * call, ever (NFR-1); the ArchUnit suite enforces it. Coverage gate: 100% line and branch, PIT at least 90%
 * (Document 6). Built on day 3.
 */
@NullMarked
package com.liorshaya.policypilot.engine;

import org.jspecify.annotations.NullMarked;
