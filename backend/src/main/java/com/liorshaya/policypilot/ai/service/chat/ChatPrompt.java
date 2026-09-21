package com.liorshaya.policypilot.ai.service.chat;

import com.liorshaya.policypilot.ai.PromptSpec;
import com.liorshaya.policypilot.ai.prompt.PromptDefinition;
import com.liorshaya.policypilot.ai.prompt.PromptRegistry;
import com.liorshaya.policypilot.ai.prompt.Sections;
import com.liorshaya.policypilot.rag.service.RetrievedChunk;
import com.liorshaya.policypilot.rules.model.Language;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The answer prompt of one turn (Document 4, Prompt 4, and Context assembly): the chunks in fused rank order as
 * {@code <chunk id="p:7" kind="paragraph">}, the history, the question and the not-covered sentence of the rule set's
 * language, every piece of user text escaped (Document 4, Data delimiters).
 */
public final class ChatPrompt {

    private ChatPrompt() {}

    public static PromptSpec spec(PromptDefinition answer, int versionNo, String domain, Language language,
            List<RetrievedChunk> chunks, ChatHistory history, String question, String notCoveredSentence) {
        String languageName = PromptRegistry.languageName(language.json());
        String context = chunks.stream()
                .map(chunk -> "<chunk id=\"" + chunk.id() + "\" kind=\"" + chunk.kind().name().toLowerCase(Locale.ROOT)
                        + "\">\n" + Sections.escape(chunk.text()) + "\n</chunk>")
                .collect(Collectors.joining("\n"));
        String user = answer.user().render(Map.of(
                "versionNo", Integer.toString(versionNo),
                "rulesetId", domain,
                "language", languageName,
                "chunks", context,
                "turnCount", Integer.toString(history.turnCount()),
                "history", history.text(),
                "question", Sections.escape(question),
                "notCoveredSentence", notCoveredSentence));
        String system = answer.system().render(Map.of("language", languageName));
        return new PromptSpec(answer.name(), answer.version(), answer.role(), system, user, null, answer.temperature(),
                answer.maxOutputTokens(), answer.timeout(), 1);
    }
}
