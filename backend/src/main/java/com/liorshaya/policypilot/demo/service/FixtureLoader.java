package com.liorshaya.policypilot.demo.service;

import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the demo policy on an empty database (Document 2, Local: a seed job loads the fixtures; Work Plan day 4:
 * loading of the lending policy fixture) as the protected row every sandbox reads and none may modify. It runs at
 * startup and does nothing when a protected policy already exists. The fixture text is clean as committed (NFC, no
 * format or control characters, checked by FixtureSeedIT), so it is stored as it is.
 */
@Component
public class FixtureLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(FixtureLoader.class);

    private final PolicyService policies;

    public FixtureLoader(PolicyService policies) {
        this.policies = policies;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (!policies.protectedPolicies().isEmpty()) {
            return;
        }
        LendingFixture fixture = LendingFixture.load();
        PolicyView seeded = policies.createProtected(fixture.title(),
                PolicyLanguage.fromCode(fixture.language()).orElseThrow(), fixture.text());
        LOG.atInfo().setMessage("demo.seed").addKeyValue("policy", seeded.id())
                .addKeyValue("paragraphs", seeded.versions().getFirst().paragraphs().size()).log();
    }
}
