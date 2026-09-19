package com.liorshaya.policypilot.ruleset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.audit.service.AuditAction;
import com.liorshaya.policypilot.audit.service.AuditEntry;
import com.liorshaya.policypilot.audit.service.AuditLog;
import com.liorshaya.policypilot.policy.service.PolicyService;
import com.liorshaya.policypilot.rules.validation.ValidationContext;
import com.liorshaya.policypilot.ruleset.service.RulesetInvalidException;
import com.liorshaya.policypilot.ruleset.service.RulesetService;
import com.liorshaya.policypilot.ruleset.service.VersionStatus;
import com.liorshaya.policypilot.ruleset.service.VersionStatusException;
import com.liorshaya.policypilot.ruleset.service.VersionView;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import com.liorshaya.policypilot.support.Fixtures;
import com.liorshaya.policypilot.support.Requirement;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * {@code POST /rulesets/{id}/versions/{no}/publish} as one transaction: validate, compile, snapshot the rules and
 * write the audit entry (Work Plan day 5; Document 2, API Surface and the NFR-3 row; Document 3, Publishing gate).
 */
@Requirement({"FR-7", "NFR-3"})
class PublishTransactionIT extends ApiIntegrationTest {

    @Autowired
    private PolicyService policies;

    @Autowired
    private RulesetService rulesets;

    @Autowired
    private AuditLog audit;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MeterRegistry registry;

    private RulesetFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new RulesetFixtures(policies, rulesets);
    }

    // Document 2, ruleset_version columns; Document 5: the actor is the sandbox id. Expected: START and the sandbox
    @Test
    void publishTurnsTheDraftIntoAPublishedVersionStampedWithTheClockAndTheSandbox() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);

        VersionView published = rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow();

        assertThat(published.status()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(published.publishedAt()).isEqualTo(START);
        assertThat(published.publishedBy()).isEqualTo(sandbox.toString());
    }

    // Document 2, NFR-3: every publish writes an AuditEntry. Expected: one PUBLISH entry naming the version
    @Test
    void publishWritesExactlyOnePublishAuditEntry() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);

        VersionView published = rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow();

        List<AuditEntry> entries = audit.forVersion(published.versionId());
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.action()).isEqualTo(AuditAction.PUBLISH);
            assertThat(entry.actor()).isEqualTo(sandbox.toString());
            assertThat(entry.at()).isEqualTo(START);
            assertThat(entry.details().path("rules").asInt()).isEqualTo(Fixtures.lendingV1().withArray("rules").size());
        });
    }

    // Document 2, rule table. Expected: the rules of ruleset.v1.json with their provenance kind and paragraph
    @Test
    void publishDenormalizesOneRuleRowPerRule() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        JsonNode fixture = Fixtures.lendingV1();

        VersionView published = rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow();

        List<String> stored = jdbc.sql("""
                select rule_id || ':' || provenance_kind || ':' ||
                       case when paragraph_id is null then 'none' else 'paragraph' end
                from rule where ruleset_version_id = :id order by priority, rule_id
                """).param("id", published.versionId()).query(String.class).list();
        List<String> expected = fixture.withArray("rules").valueStream()
                .sorted((a, b) -> a.path("priority").asInt() - b.path("priority").asInt())
                .map(rule -> rule.path("id").asString() + ":" + rule.path("provenance").path("kind").asString() + ":"
                        + ("quoted".equals(rule.path("provenance").path("kind").asString()) ? "paragraph" : "none"))
                .toList();
        assertThat(stored).isEqualTo(expected);
    }

    // Document 3, Publishing gate. Expected: the code of fixtures/conformance/invalid-REFER_PRECEDES_REJECT.json
    @Test
    void publishListsTheOpenWarningsInTheAuditEntry() {
        UUID sandbox = UUID.randomUUID();
        String warned = Fixtures.json("conformance/invalid-REFER_PRECEDES_REJECT.json").path("code").asString();
        VersionView draft = fixtures.draft(sandbox, RulesetFixtures.lendingWithPriority("R-320", 210),
                ValidationContext.ANALYST_EDIT, Set.of());

        VersionView published = rulesets.publish(draft.rulesetId(), 1, sandbox).orElseThrow();

        JsonNode warnings = audit.forVersion(published.versionId()).getFirst().details().path("warnings");
        assertThat(warnings.valueStream().map(warning -> warning.path("code").asString()).toList()).contains(warned);
    }

    // Document 3, PROVENANCE_PENDING_AT_PUBLISH. Expected: the code of invalid-PROVENANCE_PENDING_AT_PUBLISH.json
    @Test
    void publishWithPendingProvenanceIs422AndPublishesNothing() {
        UUID sandbox = UUID.randomUUID();
        JsonNode fixture = Fixtures.json("conformance/invalid-PROVENANCE_PENDING_AT_PUBLISH.json");
        ObjectNode document = (ObjectNode) fixture.path("ruleset");
        VersionView draft = fixtures.draft(sandbox, document, ValidationContext.CHANGE_PROPOSAL, Set.of("R-170"));

        assertThatThrownBy(() -> rulesets.publish(draft.rulesetId(), 1, sandbox))
                .isInstanceOf(RulesetInvalidException.class)
                .extracting(problems -> ((RulesetInvalidException) problems).problems().getFirst().code())
                .isEqualTo(fixture.path("code").asString());
        assertThat(rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().status())
                .isEqualTo(VersionStatus.DRAFT);
    }

    // Document 2: the audit entry is written in the same transaction. Expected: the draft and the rule table unchanged
    @Test
    void publishIsAtomicWhenTheAuditWriteFails() throws SQLException {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);

        refuseAuditWritesOf(sandbox);
        try {
            assertThatThrownBy(() -> rulesets.publish(draft.rulesetId(), 1, sandbox)).isInstanceOf(Exception.class);
        } finally {
            allowAuditWritesAgain(sandbox);
        }

        assertThat(rulesets.version(draft.rulesetId(), 1, sandbox).orElseThrow().status())
                .isEqualTo(VersionStatus.DRAFT);
        assertThat(jdbc.sql("select count(*) from rule where ruleset_version_id = :id")
                .param("id", draft.versionId()).query(Integer.class).single()).isZero();
    }

    // Brief FR-7; Document 2: publish a DRAFT version. Expected: VERSION_STATUS_CONFLICT's exception
    @Test
    void publishingAPublishedVersionIsRefused() {
        UUID sandbox = UUID.randomUUID();
        VersionView draft = fixtures.draft(sandbox);
        rulesets.publish(draft.rulesetId(), 1, sandbox);

        assertThatThrownBy(() -> rulesets.publish(draft.rulesetId(), 1, sandbox))
                .isInstanceOf(VersionStatusException.class);
    }

    // Document 5, Authorization (protected demo). Expected: refused, security.protected.write_attempt plus one
    @Test
    void publishOnTheProtectedVersionIsRefusedAndCounted() {
        UUID sandbox = UUID.randomUUID();
        UUID seeded = fixtures.seeded().id();
        double before = registry.counter("security.protected.write_attempt", "entity", "ruleset").count();

        assertThatThrownBy(() -> rulesets.publish(seeded, 1, sandbox)).isInstanceOf(VersionStatusException.class);

        // test classes run in parallel and share the registry, so the assertion is on this test's own increment
        assertThat(registry.counter("security.protected.write_attempt", "entity", "ruleset").count() - before)
                .isGreaterThanOrEqualTo(1.0);
        assertThat(fixtures.seededVersion(sandbox).publishedBy()).isEqualTo(RulesetService.DEMO_ACTOR);
    }

    /** A trigger of the test's own, created as the database owner: this sandbox's audit entries are refused. */
    private static void refuseAuditWritesOf(UUID sandbox) throws SQLException {
        owner("""
                create function refuse_audit_%1$s() returns trigger language plpgsql as $$
                begin
                    if new.actor = '%2$s' then
                        raise exception 'the test refuses this audit entry';
                    end if;
                    return new;
                end
                $$;
                create trigger refuse_audit_%1$s before insert on audit_entry
                    for each row execute function refuse_audit_%1$s();
                """.formatted(name(sandbox), sandbox));
    }

    private static void allowAuditWritesAgain(UUID sandbox) throws SQLException {
        owner("drop trigger refuse_audit_%1$s on audit_entry; drop function refuse_audit_%1$s();"
                .formatted(name(sandbox)));
    }

    /** A name of this sandbox's own, so tests running in parallel never share a trigger. */
    private static String name(UUID sandbox) {
        return "t" + sandbox.toString().replace("-", "");
    }

    private static void owner(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
