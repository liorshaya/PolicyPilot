package com.liorshaya.policypilot.engine;

import com.liorshaya.policypilot.rules.json.RuleSetMapper;
import com.liorshaya.policypilot.support.Fixtures;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import tools.jackson.databind.node.ObjectNode;

/**
 * The engine micro-benchmark (Document 6, Performance and Determinism): the lending version 1 compiled once and the
 * 200 fixture cases, reported as the average time per case; the target is under 100 microseconds on the CI runner.
 * Run from {@code backend/} with {@code ./mvnw -Pbenchmark -DskipTests test-compile exec:exec}.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class EngineBenchmark {

    private final RuleEngine engine = new RuleEngine();
    private CompiledRuleSet lending;
    private List<ObjectNode> cases;

    @Setup
    public void load() {
        lending = CompiledRuleSet.compile(new RuleSetMapper().toRuleSet(Fixtures.lendingV1()));
        cases = new ArrayList<>();
        Fixtures.json("policies/consumer-lending/cases-200.json").get("cases")
                .forEach(fixtureCase -> cases.add((ObjectNode) fixtureCase.get("input")));
    }

    @Benchmark
    @OperationsPerInvocation(200)
    public void perCase(Blackhole blackhole) {
        for (ObjectNode input : cases) {
            blackhole.consume(engine.evaluate(lending, input));
        }
    }
}
