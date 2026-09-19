package com.liorshaya.policypilot.engine;

/** What the engine returns for one case: a decision, or a case error when the case is not valid. */
public sealed interface Evaluation permits Decision, CaseError {}
