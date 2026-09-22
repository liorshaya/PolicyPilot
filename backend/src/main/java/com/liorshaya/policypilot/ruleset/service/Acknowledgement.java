package com.liorshaya.policypilot.ruleset.service;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * An analyst's acknowledgement of one review finding: the resolution a gap needs, the note an error needs, who and
 * when (Document 2, {@code POST .../findings/{findingId}/acknowledge}).
 */
public record Acknowledgement(@Nullable GapResolution resolution, @Nullable String note, String actor, Instant at) {}
