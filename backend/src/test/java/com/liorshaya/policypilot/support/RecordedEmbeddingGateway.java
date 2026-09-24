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
 * fails the test, so a change to what is embedded needs a live pass rather than a silent stand-in. Each provider's
 * vectors are replayed from their own folder, since one model's vectors mean nothing to another's.
 */
public final class RecordedEmbeddingGateway implements EmbeddingGateway {

    /** Where the live passes wrote OpenAI's vectors, relative to {@code backend/}: what the offline tests replay. */
    public static final Path RECORDINGS = directoryOf("openai", "text-embedding-3-small");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Path directory;
    private final Map<String, float[]> vectors;
    private final int dimension;

    private RecordedEmbeddingGateway(Path directory, Map<String, float[]> vectors, int dimension) {
        this.directory = directory;
        this.vectors = vectors;
        this.dimension = dimension;
    }

    /**
     * Where a live pass writes the vectors of a provider's embedding model, relative to {@code backend/}:
     * {@code fixtures/eval/recordings/<provider>/embedding/<model>/}.
     */
    public static Path directoryOf(String provider, String model) {
        return Path.of("..", "fixtures", "eval", "recordings", provider, "embedding", model);
    }

    /** Every recording under {@link #RECORDINGS}. */
    public static RecordedEmbeddingGateway replaying(int dimension) {
        return replaying(RECORDINGS, dimension);
    }

    /**
     * Every recording under a provider's folder, none when no pass has recorded there yet. A vector of another size
     * than the profile's column fails here, before any is stored: vectors of one model replayed under a profile sized
     * for another (1536 for text-embedding-3-small, 1024 for bge-m3) would not fit the {@code chunk} column.
     */
    public static RecordedEmbeddingGateway replaying(Path directory, int dimension) {
        Map<String, float[]> vectors = new HashMap<>();
        if (!Files.isDirectory(directory)) {
            return new RecordedEmbeddingGateway(directory, vectors, dimension);
        }
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                JsonNode recording = JSON.readTree(Files.readString(file));
                for (JsonNode entry : recording.required("embeddings")) {
                    float[] vector = decode(entry.required("vector").asString());
                    if (vector.length != dimension) {
                        throw new IllegalStateException(file + " holds vectors of " + vector.length
                                + " dimensions, the profile has " + dimension);
                    }
                    vectors.put(entry.required("sha256").asString(), vector);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the embedding recordings", e);
        }
        return new RecordedEmbeddingGateway(directory, vectors, dimension);
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
                throw new IllegalStateException("no recorded embedding for \"" + text + "\" in " + directory
                        + "; run LiveRetrievalRecordingIT");
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
