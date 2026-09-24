package com.liorshaya.policypilot.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.liorshaya.policypilot.demo.service.SandboxService;
import com.liorshaya.policypilot.policy.service.PolicyLanguage;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Seeded;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Protected rows with fork on write, at the service level (Document 5, Authorization (sandbox): "the seeded rows
 * carry the protected flag and a null sandbox id, and any write against them is refused and forks a sandbox copy
 * instead"; Work Plan day 4). The first route that writes to a protected row arrives on day 5.
 */
class ForkOnWriteIT extends ApiIntegrationTest {

    @Autowired
    private SandboxService sandboxes;

    @Autowired
    private PolicyService policies;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void writeToTheProtectedPolicyForksACopyIntoTheCallersSandbox() {
        UUID sandbox = UUID.randomUUID();
        PolicyView seeded = seeded();

        PolicyView target = sandboxes.policyForWrite(seeded.id(), sandbox).orElseThrow();

        assertThat(target.id()).isNotEqualTo(seeded.id());
        assertThat(target.isProtected()).isFalse();
        assertThat(target.forkedFromId()).isEqualTo(seeded.id());
        assertThat(policies.find(target.id(), sandbox)).isPresent();
    }

    @Test
    void forkLeavesTheProtectedOriginalByteIdentical() throws IOException {
        PolicyView seeded = seeded();

        sandboxes.policyForWrite(seeded.id(), UUID.randomUUID());

        String raw = jdbc.sql("select raw_text from policy_version where document_id = :id")
                .param("id", seeded.id()).query(String.class).single();
        assertThat(raw).isEqualTo(Files.readString(Fixtures.path("policies/consumer-lending/policy.he.md")));
        assertThat(policies.protectedPolicies()).contains(seeded).hasSize(2);
    }

    @Test
    void forkKeepsEveryParagraphAndItsIndex() {
        PolicyView seeded = seeded();

        PolicyView copy = sandboxes.policyForWrite(seeded.id(), UUID.randomUUID()).orElseThrow();

        assertThat(copy.versions().getFirst().paragraphs()).isEqualTo(seeded.versions().getFirst().paragraphs());
    }

    @Test
    void secondWriteFromTheSameSandboxReusesItsFork() {
        UUID sandbox = UUID.randomUUID();
        UUID seededId = seeded().id();

        UUID first = sandboxes.policyForWrite(seededId, sandbox).orElseThrow().id();
        UUID second = sandboxes.policyForWrite(seededId, sandbox).orElseThrow().id();

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.sql("select count(*) from policy_document where sandbox_id = :s")
                .param("s", sandbox).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void twoSandboxesGetTwoForks() {
        UUID seededId = seeded().id();

        UUID one = sandboxes.policyForWrite(seededId, UUID.randomUUID()).orElseThrow().id();
        UUID two = sandboxes.policyForWrite(seededId, UUID.randomUUID()).orElseThrow().id();

        assertThat(one).isNotEqualTo(two);
    }

    @Test
    void protectedWriteAttemptIncrementsItsCounter() {
        double before = registry.counter("security.protected.write_attempt", "entity", "policy").count();

        sandboxes.policyForWrite(seeded().id(), UUID.randomUUID());

        assertThat(registry.counter("security.protected.write_attempt", "entity", "policy").count() - before)
                .isEqualTo(1.0);
    }

    @Test
    void aSandboxsOwnPolicyIsWrittenInPlace() {
        UUID sandbox = UUID.randomUUID();
        PolicyView own = policies.create(sandbox, "Own", PolicyLanguage.EN, "Applicants must be 21.");

        assertThat(sandboxes.policyForWrite(own.id(), sandbox)).contains(own);
    }

    @Test
    void anotherSandboxsPolicyCannotBeWritten() {
        PolicyView foreign = policies.create(UUID.randomUUID(), "Foreign", PolicyLanguage.EN, "Applicants must be 21.");

        assertThat(sandboxes.policyForWrite(foreign.id(), UUID.randomUUID())).isEmpty();
    }

    private PolicyView seeded() {
        return Seeded.lendingPolicy(policies);
    }
}
