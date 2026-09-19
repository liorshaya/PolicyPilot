package com.liorshaya.policypilot.demo.service;

import com.liorshaya.policypilot.common.SecurityEvents;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.policy.service.PolicyView;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Fork on write (Document 2, {@code demo.SandboxService}; Document 5, Authorization (sandbox) and (protected demo)):
 * a write against a protected row is refused and lands on the sandbox's own copy instead, made on the first write
 * and reused after; the attempt counts in {@code security.protected.write_attempt}. Routes that write to policies
 * or rule sets ask for the row to write here first (day 5 onward).
 */
@Service
public class SandboxService {

    static final String POLICY = "policy";

    private final PolicyService policies;
    private final SecurityEvents events;

    public SandboxService(PolicyService policies, SecurityEvents events) {
        this.policies = policies;
        this.events = events;
    }

    /**
     * The policy a write from {@code sandboxId} goes to: its own policy as it is, or its copy of a protected one;
     * empty when the sandbox cannot see {@code policyId} at all.
     */
    public Optional<PolicyView> policyForWrite(UUID policyId, UUID sandboxId) {
        return policies.find(policyId, sandboxId).map(policy -> {
            if (!policy.isProtected()) {
                return policy;
            }
            events.protectedWriteAttempt(POLICY, sandboxId);
            return policies.forkOf(policy.id(), sandboxId);
        });
    }
}
