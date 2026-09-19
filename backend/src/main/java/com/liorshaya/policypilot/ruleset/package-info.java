/**
 * Rule sets and their versions (Document 2, Backend Module Structure): drafts, manual edits, publishing with its
 * audit entry, immutability, the {@code rule} rows, the compiled rule set cached per version, and fork on write of
 * protected rule sets.
 *
 * <p>Allowed dependencies: {@code rules}, {@code engine}, {@code policy}, {@code audit}, persistence, {@code config},
 * {@code common}. Built on day 5.
 */
package com.liorshaya.policypilot.ruleset;
