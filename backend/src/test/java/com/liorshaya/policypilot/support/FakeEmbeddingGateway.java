package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.ai.LlmUnavailableException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The embedding provider in every test (Document 6, Test doubles policy: the model gateways are the only things
 * faked). Vectors are chosen by the test, so every cosine similarity a test expects can be worked out by hand: a text
 * the test registered gets its vector, and any other text gets the first unit axis. Two markers inside a text let a
 * test drive the job without timing: {@link #FAIL} makes the call fail as a provider would, and {@link #WAIT} holds it
 * until the test calls {@link #release}. The fake is shared by a whole Spring context, so each test keys its
 * behavior by text of its own rather than by a switch another test could flip.
 */
public final class FakeEmbeddingGateway implements EmbeddingGateway {

    /** A text containing this fails the call. */
    public static final String FAIL = "embedding-fails";
    /** A text containing {@code WAIT + key} waits for {@code release(key)}. */
    public static final String WAIT = "embedding-waits:";

    private final int dimension;
    private final Map<String, float[]> vectors = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    private final List<String> embedded = new ArrayList<>();

    public FakeEmbeddingGateway(int dimension) {
        this.dimension = dimension;
    }

    /** A unit vector along {@code axis}: the cosine of two of them is 1 on the same axis and 0 on different ones. */
    public float[] axis(int axis) {
        float[] vector = new float[dimension];
        vector[axis] = 1f;
        return vector;
    }

    /** From now on, {@code text} embeds as {@code vector}. */
    public FakeEmbeddingGateway register(String text, float[] vector) {
        vectors.put(text, vector.clone());
        return this;
    }

    /** Lets the calls holding on {@code WAIT + key} finish. */
    public void release(String key) {
        gate(key).countDown();
    }

    /** Whether any call so far asked for {@code text}. */
    public synchronized boolean embedded(String text) {
        return embedded.contains(text);
    }

    /** How many times a call asked for {@code text}: a count no other test's texts can move. */
    public synchronized long timesEmbedded(String text) {
        return embedded.stream().filter(text::equals).count();
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        synchronized (this) {
            embedded.addAll(texts);
        }
        for (String text : texts) {
            if (text.contains(FAIL)) {
                throw new LlmUnavailableException(LlmUnavailableException.Reason.PROVIDER_ERROR, "fake provider error");
            }
            int wait = text.indexOf(WAIT);
            if (wait >= 0) {
                await(text.substring(wait + WAIT.length()).split("\\s", 2)[0]);
            }
        }
        return texts.stream().map(text -> vectors.getOrDefault(text, axis(0)).clone()).toList();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private void await(String key) {
        try {
            if (!gate(key).await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the test never released " + key);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private CountDownLatch gate(String key) {
        return gates.computeIfAbsent(key, ignored -> new CountDownLatch(1));
    }
}
