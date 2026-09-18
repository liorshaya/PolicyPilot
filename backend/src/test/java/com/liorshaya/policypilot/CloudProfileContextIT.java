package com.liorshaya.policypilot;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.support.PostgresContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Work Plan day 1: the context loads with the Railway combination {@code openai,cloud}. */
@SpringBootTest(properties = "spring.ai.openai.api-key=test-key-not-real")
@ActiveProfiles({"openai", "cloud"})
class CloudProfileContextIT extends PostgresContainerSupport {

    @Autowired
    private PolicyPilotProperties properties;

    @Test
    void contextLoadsWithTheCloudProfile() {
        assertThat(properties).isNotNull();
    }

    @Test
    void payloadLoggingIsOffInTheCloud() {
        assertThat(properties.ai().logPayloads()).isFalse();
    }
}
