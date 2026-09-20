package com.liorshaya.policypilot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liorshaya.policypilot.ai.adapter.ModelCallLedger;
import com.liorshaya.policypilot.ai.adapter.TokenBudgetGuard;
import com.liorshaya.policypilot.ai.cache.ProposalCache;
import com.liorshaya.policypilot.ai.entity.ModelCallEntity;
import com.liorshaya.policypilot.ai.repository.ModelCallRepository;
import com.liorshaya.policypilot.support.ApiIntegrationTest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

/**
 * What the AI layer records about itself (Document 4, Logging for every call; Document 5, Spend caps): the ledger
 * row of every call, the daily token budget that stops spending, and the response cache that makes a scripted
 * step free the second time. The budget here is 100 tokens so a test can reach it; the real number is in
 * {@code application.yml}.
 *
 * <p>The ledger is append-only by its grants, so nothing here deletes a row: each test writes under its own
 * prompt version and reads that back. Each test also gets its own day, because a day's spending is shared state.
 * The class is isolated because it moves the clock.
 */
@TestPropertySource(properties = "policypilot.ai.daily-token-budget=100")
@Isolated
class AiBookkeepingIT extends ApiIntegrationTest {

    private static final AtomicInteger DAY = new AtomicInteger();
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TokenBudgetGuard budget;

    @Autowired
    private ModelCallLedger ledger;

    @Autowired
    private ModelCallRepository calls;

    @Autowired
    private ProposalCache cache;

    @BeforeEach
    void startOnADayOfItsOwn() {
        clock.set(START.plus(Duration.ofDays(DAY.incrementAndGet())));
    }

    private static PromptSpec spec(String version) {
        return new PromptSpec(
                "author",
                version,
                ModelRole.STRONG,
                "system text",
                "user text",
                "schemas/ruleset-1.0.schema.json",
                0.0,
                8000,
                Duration.ofSeconds(60),
                1);
    }

    @Test
    void aCallUnderTheBudgetIsAllowedAndCounted() {
        budget.requireBudget();
        budget.record(new TokenUsage(12, 6));

        assertThat(budget.spentToday()).isEqualTo(18);
        assertThat(budget.stopped()).isFalse();
    }

    @Test
    void theBudgetStopsSpendingOnceTheDayHasReachedIt() {
        budget.record(new TokenUsage(60, 60));

        assertThat(budget.stopped()).isTrue();
        assertThatThrownBy(budget::requireBudget)
                .isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("token budget")
                .extracting(thrown -> ((LlmUnavailableException) thrown).reason())
                .isEqualTo(LlmUnavailableException.Reason.BUDGET_EXHAUSTED);
    }

    @Test
    void aNewDayStartsWithTheWholeBudgetAgain() {
        budget.record(new TokenUsage(60, 60));
        assertThat(budget.stopped()).isTrue();

        clock.advance(Duration.ofDays(1));

        assertThat(budget.spentToday()).isZero();
        assertThat(budget.stopped()).isFalse();
        budget.requireBudget();
    }

    @Test
    void anAnswerFromTheCacheCostsNothing() {
        budget.record(TokenUsage.NONE);

        assertThat(budget.spentToday()).isZero();
    }

    @Test
    void everyCallIsRecordedWithWhatDocument4Lists() {
        PromptSpec spec = spec("v1-ledger");

        ledger.record(spec, "gpt-5.6-terra", "openai", new TokenUsage(12_000, 6_000), Duration.ofMillis(4_200),
                ModelCallLedger.Results.VALID, false);

        List<ModelCallEntity> written = recorded("v1-ledger");
        assertThat(written).hasSize(1);
        ModelCallEntity call = written.getFirst();
        assertThat(call.promptName()).isEqualTo("author");
        assertThat(call.promptVersion()).isEqualTo("v1-ledger");
        assertThat(call.model()).isEqualTo("gpt-5.6-terra");
        assertThat(call.provider()).isEqualTo("openai");
        assertThat(call.attempt()).isEqualTo(1);
        assertThat(call.inputTokens()).isEqualTo(12_000);
        assertThat(call.outputTokens()).isEqualTo(6_000);
        assertThat(call.latencyMs()).isEqualTo(4_200);
        assertThat(call.validationResult()).isEqualTo("VALID");
        assertThat(call.cacheHit()).isFalse();
        assertThat(call.at()).isEqualTo(clock.instant());
    }

    @Test
    void aRepairIsRecordedAsTheSecondAttemptOfTheSamePrompt() {
        PromptSpec spec = spec("v1-repair");

        ledger.record(spec, "gpt-5.6-terra", "openai", new TokenUsage(10, 10), Duration.ofMillis(1),
                ModelCallLedger.Results.INVALID, false);
        ledger.record(spec.repairedWith("fix it"), "gpt-5.6-terra", "openai", new TokenUsage(10, 10),
                Duration.ofMillis(1), ModelCallLedger.Results.VALID, false);

        assertThat(recorded("v1-repair"))
                .extracting(ModelCallEntity::attempt, ModelCallEntity::validationResult)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1, "INVALID"),
                        org.assertj.core.groups.Tuple.tuple(2, "VALID"));
    }

    @Test
    void anAnswerServedFromTheCacheIsRecordedAsACacheHit() {
        ledger.record(spec("v1-hit"), "gpt-5.6-terra", "openai", TokenUsage.NONE, Duration.ofMillis(2),
                ModelCallLedger.Results.VALID, true);

        ModelCallEntity call = recorded("v1-hit").getFirst();
        assertThat(call.cacheHit()).isTrue();
        assertThat(call.inputTokens()).isZero();
        assertThat(call.outputTokens()).isZero();
    }

    @Test
    void theCacheAnswersTheSameCallAndMissesANewPromptVersion() throws Exception {
        PromptSpec spec = spec("v1-cache");
        String key = ProposalCache.keyOf(spec, "gpt-5.6-terra");

        cache.put(key, "author", "{\"id\":\"consumer-lending\"}");

        assertThat(cache.find(key)).isPresent();
        assertThat(JSON.readTree(cache.find(key).orElseThrow()))
                .isEqualTo(JSON.readTree("{\"id\":\"consumer-lending\"}"));
        // Document 4: the cache key includes the prompt version, so bumping it invalidates the demo cache
        assertThat(cache.find(ProposalCache.keyOf(spec("v2-cache"), "gpt-5.6-terra"))).isEmpty();
        // and so does another model, because it would not have produced the same answer
        assertThat(cache.find(ProposalCache.keyOf(spec, "gpt-5.6-luna"))).isEmpty();
    }

    private List<ModelCallEntity> recorded(String version) {
        return calls.findByPromptNameAndPromptVersionOrderByAtAsc("author", version);
    }
}
