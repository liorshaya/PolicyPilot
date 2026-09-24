package com.liorshaya.policypilot.demo.service;

import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.demo.repository.SandboxPurge;
import java.time.Clock;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The demo reset (Document 2, {@code demo.ResetJob} and {@code POST /admin/reset}; Document 5, Availability, Nightly
 * reset, and Data Protection, Deletion): deletes every sandbox idle for 24 hours with everything it holds, re-seeds
 * the protected rows if any is missing, and writes one RESET audit entry, all in one transaction; then logs
 * {@code demo.reset}. It runs nightly on {@code policypilot.demo.reset-cron} and on demand for the presenter. The
 * response cache, the token ledger and the model call log are left as they are: the scripted demo answers live in the
 * cache (decided 2026-09-27, day 15).
 */
@Service
public class ResetJob {

    /** The actor of the nightly reset's audit entry; a manual reset's actor is the presenter's sandbox. */
    public static final String NIGHTLY_ACTOR = "nightly-reset";

    private static final Logger LOG = LoggerFactory.getLogger(ResetJob.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** What started a reset. */
    public enum Trigger {
        NIGHTLY,
        MANUAL;

        String tag() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What a reset did: the sandboxes it deleted and whether it had to re-seed. */
    public record Result(int sandboxesDeleted, boolean reseeded) {}

    private final SandboxPurge purge;
    private final FixtureLoader seed;
    private final AuditLog audit;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public ResetJob(SandboxPurge purge, FixtureLoader seed, AuditLog audit, TransactionTemplate transactions,
            Clock clock) {
        this.purge = purge;
        this.seed = seed;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Scheduled(cron = "${policypilot.demo.reset-cron}", zone = "UTC")
    public void nightly() {
        reset(Trigger.NIGHTLY, NIGHTLY_ACTOR);
    }

    /** Resets now; {@code actor} is who the RESET entry names. */
    public Result reset(Trigger trigger, String actor) {
        ObjectNode details = transactions.execute(status -> {
            SandboxPurge.Purged purged = purge.purge(clock.instant());
            boolean reseeded = seed.seed();
            ObjectNode written = JSON.createObjectNode().put("trigger", trigger.tag())
                    .put("sandboxesDeleted", purged.sandboxes()).put("policiesDeleted", purged.policies())
                    .put("versionsDeleted", purged.versions()).put("decisionsDeleted", purged.decisions())
                    .put("chatSessionsDeleted", purged.chatSessions())
                    .put("changeRequestsDeleted", purged.changeRequests())
                    .put("auditEntriesDeleted", purged.auditEntries()).put("reseeded", reseeded);
            audit.append(AuditAction.RESET, actor, written);
            return written;
        });
        Result result = new Result(details.required("sandboxesDeleted").asInt(),
                details.required("reseeded").asBoolean());
        LOG.atInfo().setMessage("demo.reset").addKeyValue("trigger", trigger.tag())
                .addKeyValue("sandboxesDeleted", result.sandboxesDeleted())
                .addKeyValue("reseeded", result.reseeded()).log();
        return result;
    }
}
