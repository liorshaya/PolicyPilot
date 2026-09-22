package com.liorshaya.policypilot.support;

import java.nio.file.Path;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * The model of the integration tests that script it or replay it (Document 6, AI Layer Testing): one
 * {@link RecordedGateway} that answers what a test scripted first and replays the committed recordings otherwise. Every
 * such test imports this one configuration, so they share one Spring context and one connection pool rather than one
 * each; a test that scripts it is {@code @Isolated} and resets it first.
 */
@TestConfiguration(proxyBeanMethods = false)
public class RecordedModel {

    /** The committed recordings of the openai profile, the directory LiveReviewRecordingIT and its peers write. */
    public static final Path RECORDINGS = Path.of("..", "fixtures", "eval", "recordings", "openai");

    @Bean
    @Primary
    RecordedGateway recordedGateway() {
        return RecordedGateway.replaying(RECORDINGS);
    }
}
