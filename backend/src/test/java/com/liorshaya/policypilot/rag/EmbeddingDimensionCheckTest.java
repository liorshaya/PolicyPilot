package com.liorshaya.policypilot.rag;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.rag.service.EmbeddingDimensionCheck;
import com.liorshaya.policypilot.support.Requirement;
import org.junit.jupiter.api.Test;

/**
 * Document 2, Storage: "the dimension is part of the profile and checked at startup", so a profile switch cannot write
 * vectors the column cannot hold. The column's size comes from the database and the gateway's from the profile.
 */
@Requirement("FR-21")
class EmbeddingDimensionCheckTest {

    // Expected: 1536 against 1536 (openai) starts
    @Test
    void matchingDimensionsStart() {
        assertThatCode(() -> EmbeddingDimensionCheck.verify(1536, 1536)).doesNotThrowAnyException();
    }

    // Document 2, line on profile switching: an existing database needs a re-embed job. Expected: bge-m3's 1024
    // against a 1536 column refuses to start and names both numbers
    @Test
    void aGatewayOfAnotherDimensionRefusesToStart() {
        assertThatThrownBy(() -> EmbeddingDimensionCheck.verify(1536, 1024))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vector(1536)")
                .hasMessageContaining("1024");
    }
}
