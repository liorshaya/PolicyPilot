package com.liorshaya.policypilot.ai.prompt;

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
}
