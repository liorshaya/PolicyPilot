package com.liorshaya.policypilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * PolicyPilot: an AI copilot over a deterministic rules engine.
 *
 * <p>The model proposes and explains, the rules engine decides, a person approves every policy change
 * (Document 1, Project Brief). The package layout and the dependency direction between packages are
 * described in {@code package-info.java} of this package and enforced by the ArchUnit suite.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PolicyPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PolicyPilotApplication.class, args);
    }
}
