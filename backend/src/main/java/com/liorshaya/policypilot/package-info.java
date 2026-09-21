/**
 * Root of the PolicyPilot backend.
 *
 * <p><b>Modules</b> (Document 2, Backend Module Structure): twelve packages with dependencies allowed in one
 * direction only, inward toward {@code rules} and {@code engine}; {@code PackageRulesTest} holds every arrow.
 * <pre>
 *   rules       the Rules DSL model, JSON Schema, validator          depends on: JDK, Jackson
 *   engine      RuleEngine, CompiledRuleSet, Trace, simulation        depends on: rules
 *   policy      policy documents, versions, paragraphs                depends on: rules, persistence
 *   ruleset     rule sets, versions, publishing                       depends on: rules, engine, policy, audit, persistence
 *   decision    cases, decisions, batch, simulate, statistics         depends on: ruleset, engine, rules, persistence
 *   ai          gateways, prompts, use cases, validation loop         depends on: rules, engine, policy, ruleset, decision, rag
 *   ai.adapter  the only package that imports Spring AI               depends on: Spring AI, ai interfaces, persistence
 *   rag         chunking, embeddings, hybrid retrieval, citations     depends on: policy, rules, ruleset, ai, persistence
 *   change      change requests, impact, diff, regression, approval  depends on: ai, ruleset, engine, decision, audit
 *   audit       append-only audit log                                 depends on: persistence
 *   demo        sandboxes, nightly reset, fixture loading             depends on: policy, ruleset, decision, audit, persistence
 *   web         REST controllers, SSE, DTOs, errors, security        depends on: every package above; nothing depends on it
 * </pre>
 *
 * <p><b>Layers inside a module</b> (repository layout decision of 2026-09-18): a module that owns state is
 * split into {@code service} (its public entry points), {@code entity} (JPA entities) and {@code repository}
 * (Spring Data repositories, private to the module). The HTTP layer lives in {@code web}: {@code controller},
 * {@code request}, {@code response}, {@code error}, {@code security}, {@code validation}.
 *
 * <p><b>Cross-cutting</b>: {@code config} holds Spring configuration and the typed application properties;
 * {@code common} holds small dependency-free helpers. Both depend on nothing else in the application, and
 * {@code rules} and {@code engine} do not depend on them either.
 */
package com.liorshaya.policypilot;
