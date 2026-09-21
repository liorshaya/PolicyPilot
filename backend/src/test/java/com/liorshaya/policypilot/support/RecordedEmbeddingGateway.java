package com.liorshaya.policypilot.support;

import com.liorshaya.policypilot.ai.EmbeddingGateway;
import com.liorshaya.policypilot.common.Hashes;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The embedding provider as a live pass recorded it (fixtures/eval/recordings/README.md, Embeddings): every text a
 * recording holds answers with the vector the provider returned, looked up by the text's SHA-256; any other text
 * fails the test, so a change to what is embedded needs a live pass rather than a silent stand-in.
 */
public final class RecordedEmbeddingGateway implements EmbeddingGateway {

    /** Where the live pass writes, relative to {@code backend/}. */
    public static final Path RECORDINGS =
            Path.of("..", "fixtures", "eval", "recordings", "openai", "embedding", "text-embedding-3-small");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Map<String, float[]> vectors;
    private final int dimension;

    private RecordedEmbeddingGateway(Map<String, float[]> vectors, int dimension) {
        this.vectors = vectors;
        this.dimension = dimension;
    }

    /** Every recording under {@link #RECORDINGS}. */
    public static RecordedEmbeddingGateway replaying(int dimension) {
        Map<String, float[]> vectors = new HashMap<>();
        try (Stream<Path> files = Files.list(RECORDINGS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonNode recording = JSON.readTree(Files.readString(file));
                recording.required("embeddings").forEach(entry ->
                        vectors.put(entry.required("sha256").asString(), decode(entry.required("vector").asString())));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the embedding recordings", e);
        }
        return new RecordedEmbeddingGateway(vectors, dimension);
    }

    @Override
    public float[] embed(String text) {
        return embedAll(List.of(text)).getFirst();
    }

    @Override
    public List<float[]> embedAll(List<String> texts) {
        return texts.stream().map(text -> {
            float[] vector = vectors.get(Hashes.sha256Hex(text));
            if (vector == null) {
                throw new IllegalStateException("no recorded embedding for \"" + text + "\"; run LiveRetrievalRecordingIT");
            }
            return vector.clone();
        }).toList();
    }

    @Override
    public int dimension() {
        return dimension;
    }

    /** One recording entry: the text, its SHA-256 and its vector as base64 of little-endian float32. */
    public static ObjectNode entry(String text, float[] vector) {
        ByteBuffer bytes = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            bytes.putFloat(value);
        }
        ObjectNode entry = JSON.createObjectNode();
        entry.put("sha256", Hashes.sha256Hex(text));
        entry.put("text", text);
        entry.put("vector", Base64.getEncoder().encodeToString(bytes.array()));
        return entry;
    }

    private static float[] decode(String base64) {
        ByteBuffer bytes = ByteBuffer.wrap(Base64.getDecoder().decode(base64)).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.remaining() / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = bytes.getFloat();
        }
        return vector;
    }
}
