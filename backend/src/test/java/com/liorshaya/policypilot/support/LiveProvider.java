package com.liorshaya.policypilot.support;

import org.springframework.test.context.ActiveProfilesResolver;

/**
 * The provider a live pass records, from {@code -Dprovider} ({@code openai} when it is not given), as the active
 * profile (Document 2, Configuration and Model Providers). Evaluation run 2 has a column per provider (Document 4,
 * Evaluation Set and Metrics), and each column is recorded by the same passes under its own profile.
 */
public final class LiveProvider implements ActiveProfilesResolver {

    /** The provider of this run. */
    public static String name() {
        return System.getProperty("provider", "openai");
    }

    @Override
    public String[] resolve(Class<?> testClass) {
        return new String[] {name()};
    }
}
