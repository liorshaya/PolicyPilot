package com.liorshaya.policypilot.ai.service.chat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The last defense against a secret leaving in an answer (Document 5, Sensitive information in prompts; RT-02): the
 * text shown so far is scanned for the configured secrets and for provider key prefixes, and a hit stops the stream.
 * Nothing secret is ever put in a prompt, so a hit means something else went wrong, and it is counted.
 */
public final class OutputDenylist {

    /** An OpenAI key: its prefix and enough of a body to tell it from ordinary words. */
    private static final Pattern PROVIDER_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{16,}");

    private final List<String> secrets;

    public OutputDenylist(List<String> secrets) {
        this.secrets = secrets.stream()
                .filter(secret -> secret != null && !secret.isBlank())
                .map(secret -> secret.toLowerCase(Locale.ROOT))
                .toList();
    }

    public boolean leaks(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return secrets.stream().anyMatch(lower::contains) || PROVIDER_KEY.matcher(text).find();
    }
}
