package com.liorshaya.policypilot.ai.prompt;

import com.liorshaya.policypilot.policy.service.PolicyVersionRef;
import java.util.List;

/**
 * Data sections of a rendered prompt (Document 4, Data delimiters; Document 5, RT-06): every document, chunk,
 * question, turn and tool result sits inside a section the API emits, and a {@code <} inside the text is escaped, so
 * text can never close a section or open a fake one. The model still reads the words; it cannot read them as markup.
 */
public final class Sections {

    private Sections() {}

    public static String escape(String text) {
        return text.replace("<", "&lt;");
    }

    /** Paragraphs as a policy section numbers them, one per line with its {@code [n]} prefix, each escaped. */
    public static String numbered(List<PolicyVersionRef.Paragraph> paragraphs) {
        StringBuilder text = new StringBuilder();
        for (PolicyVersionRef.Paragraph paragraph : paragraphs) {
            text.append('[').append(paragraph.index()).append("] ").append(escape(paragraph.text())).append('\n');
        }
        return text.toString().stripTrailing();
    }
}
