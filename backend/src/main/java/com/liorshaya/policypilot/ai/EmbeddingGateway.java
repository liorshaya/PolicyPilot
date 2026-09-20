package com.liorshaya.policypilot.ai;

import java.util.List;

/**
 * The one way the system embeds text (Document 2, AI Layer Design). The dimension is a property of the active
 * profile and is checked against the vector column at startup, so a profile switch cannot write vectors the
 * column cannot hold.
 */
public interface EmbeddingGateway {

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);

    /** The dimension every vector this gateway returns has. */
    int dimension();
}
