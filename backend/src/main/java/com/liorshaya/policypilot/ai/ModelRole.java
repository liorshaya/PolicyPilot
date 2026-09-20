package com.liorshaya.policypilot.ai;

/**
 * Which of the two configured models a prompt asks for (Document 4, Model Configuration per Prompt). A prompt names
 * a role, never a model, so a model upgrade is a property change.
 */
public enum ModelRole {
    /** Authoring, review and change: accuracy matters more than cost. */
    STRONG,
    /** Explanations and chat answers: fluency at high volume. */
    FAST
}
