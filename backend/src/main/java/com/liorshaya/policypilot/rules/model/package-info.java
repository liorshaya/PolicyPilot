/**
 * The Rules DSL 1.0 as immutable records (Document 3): a {@link com.liorshaya.policypilot.rules.model.RuleSet} with
 * its {@link com.liorshaya.policypilot.rules.model.Field fields}, {@link com.liorshaya.policypilot.rules.model.Rule
 * rules}, {@link com.liorshaya.policypilot.rules.model.Condition conditions},
 * {@link com.liorshaya.policypilot.rules.model.Action actions} and the three
 * {@link com.liorshaya.policypilot.rules.model.Provenance provenance} kinds.
 *
 * <p>The records are lossless: an optional attribute the document omits is {@code null}, never its default, so a
 * rule set written back is the rule set that was read. The documented defaults are read through the
 * {@code is...} methods ({@code Field.isRequired()}, {@code Rule.isEnabled()}, {@code Decide.isTerminal()}).
 */
@NullMarked
package com.liorshaya.policypilot.rules.model;

import org.jspecify.annotations.NullMarked;
