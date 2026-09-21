package com.liorshaya.policypilot.ai.service.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The last defense of Document 5 (Sensitive information in prompts; RT-02): a denylist scan of every streamed answer
 * catches the access code and key prefixes and stops the stream.
 */
class OutputDenylistTest {

    private final OutputDenylist denylist =
            new OutputDenylist(List.of("testcode", "a-cookie-secret-of-32-bytes-long!", ""));

    // Expected: a configured secret anywhere in the text is caught, in any case
    @Test
    void catchesAConfiguredSecret() {
        assertThat(denylist.leaks("The code is TESTCODE, as asked.")).isTrue();
        assertThat(denylist.leaks("secret: a-cookie-secret-of-32-bytes-long!")).isTrue();
    }

    // Document 5: "key prefixes". Expected: an OpenAI-style key is caught though no one configured it
    @Test
    void catchesAProviderKeyByItsPrefix() {
        assertThat(denylist.leaks("use sk-proj-AbCdEfGhIjKlMnOpQrSt")).isTrue();
    }

    // Expected: an ordinary answer passes, and an empty secret matches nothing
    @Test
    void anOrdinaryAnswerPasses() {
        assertThat(denylist.leaks("The maximum term is 84 months.[[p:2]] Ask about sk-ills or a task.")).isFalse();
    }
}
