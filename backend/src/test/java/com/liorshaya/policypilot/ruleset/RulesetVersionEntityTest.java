package com.liorshaya.policypilot.ruleset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.ruleset.entity.RulesetVersionEntity;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The guard a rule set version carries in Java, beside the V4 trigger that enforces it at the database (Brief FR-7;
 * Document 2, Data Model): only a DRAFT takes a new document, and a version is published once.
 */
class RulesetVersionEntityTest {

    // Document 2: rules_json is the full DSL document of a DRAFT. Expected: the new document, still a draft
    @Test
    void aDraftTakesANewDocument() {
        RulesetVersionEntity version = draft();

        version.replace("{\"id\":\"edited\"}", "[]");

        assertThat(version.getRulesJson()).isEqualTo("{\"id\":\"edited\"}");
        assertThat(version.isDraft()).isTrue();
    }

    // Document 2, ruleset_version columns. Expected: PUBLISHED with the timestamp and the actor it was given
    @Test
    void publishingStampsTheVersion() {
        RulesetVersionEntity version = draft();

        version.publish(ApiIntegrationTest.START, "demo-analyst");

        assertThat(version.getStatus()).isEqualTo("PUBLISHED");
        assertThat(version.getPublishedAt()).isEqualTo(ApiIntegrationTest.START);
        assertThat(version.getPublishedBy()).isEqualTo("demo-analyst");
        assertThat(version.isDraft()).isFalse();
    }

    // Brief FR-7: a published version is immutable. Expected: the edit is refused, the document unchanged
    @Test
    void aPublishedVersionTakesNoNewDocument() {
        RulesetVersionEntity version = draft();
        version.publish(ApiIntegrationTest.START, "demo-analyst");

        assertThatThrownBy(() -> version.replace("{}", "[]")).isInstanceOf(IllegalStateException.class);
        assertThat(version.getRulesJson()).isEqualTo("{\"id\":\"lending\"}");
    }

    // Document 3, Version lineage: a version is published once. Expected: the second publish is refused
    @Test
    void aVersionIsPublishedOnlyOnce() {
        RulesetVersionEntity version = draft();
        version.publish(ApiIntegrationTest.START, "demo-analyst");

        assertThatThrownBy(() -> version.publish(ApiIntegrationTest.START, "someone else"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static RulesetVersionEntity draft() {
        return new RulesetVersionEntity(UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(),
                "{\"id\":\"lending\"}", "[]", null);
    }
}
