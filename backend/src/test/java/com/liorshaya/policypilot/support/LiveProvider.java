package com.liorshaya.policypilot.support;

import java.nio.file.Path;
import org.springframework.core.env.Environment;
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

    /**
     * The embedding model the active profile configures, {@code spring.ai.<provider>.embedding.model}:
     * {@code text-embedding-3-small} under {@code openai}, {@code bge-m3} under {@code ollama}.
     */
    public static String embeddingModel(Environment environment) {
        return environment.getRequiredProperty("spring.ai." + name() + ".embedding.model");
    }

    /** Where this run's vectors are recorded and replayed: {@code recordings/<provider>/embedding/<model>/}. */
    public static Path embeddings(Environment environment) {
        return RecordedEmbeddingGateway.directoryOf(name(), embeddingModel(environment));
    }

    @Override
    public String[] resolve(Class<?> testClass) {
        return new String[] {name()};
    }
}
