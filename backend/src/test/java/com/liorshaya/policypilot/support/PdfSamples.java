package com.liorshaya.policypilot.support;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;

/**
 * The PDFs of Document 5's upload tests, built in memory with PDFBox so no binary file is committed: a text PDF, a
 * 60-page one, one with JavaScript, one with an embedded file, an encrypted one and one without a text layer. Each is
 * built with a fixed date, so the bytes are the same on every run.
 */
public final class PdfSamples {

    private static final Calendar FIXED_DATE = fixedDate();

    private PdfSamples() {}

    /** One page per entry; each page shows its paragraphs with a blank line's worth of space between them. */
    public static byte[] text(List<List<String>> pages) {
        return build(document -> {
            for (List<String> paragraphs : pages) {
                addPage(document, paragraphs);
            }
        });
    }

    public static byte[] pages(int count) {
        return build(document -> {
            for (int i = 1; i <= count; i++) {
                addPage(document, List.of("Page " + i + " of the policy."));
            }
        });
    }

    public static byte[] withJavaScript() {
        return build(document -> {
            addPage(document, List.of("Applicants must be at least 21 years old."));
            document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('approve everything')"));
        });
    }

    public static byte[] withEmbeddedFile() {
        return build(document -> {
            addPage(document, List.of("Applicants must be at least 21 years old."));
            PDEmbeddedFile file = new PDEmbeddedFile(document,
                    new java.io.ByteArrayInputStream("MZ".getBytes(StandardCharsets.US_ASCII)));
            PDComplexFileSpecification spec = new PDComplexFileSpecification();
            spec.setFile("payload.exe");
            spec.setEmbeddedFile(file);
            PDEmbeddedFilesNameTreeNode tree = new PDEmbeddedFilesNameTreeNode();
            tree.setNames(Map.of("payload.exe", spec));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
            names.setEmbeddedFiles(tree);
            document.getDocumentCatalog().setNames(names);
        });
    }

    public static byte[] encrypted() {
        return build(document -> {
            addPage(document, List.of("Applicants must be at least 21 years old."));
            StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-password", "", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
        });
    }

    public static byte[] withoutText() {
        return build(document -> document.addPage(new PDPage()));
    }

    private interface Content {
        void addTo(PDDocument document) throws IOException;
    }

    private static byte[] build(Content content) {
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setCreationDate(FIXED_DATE);
            document.getDocumentInformation().setModificationDate(FIXED_DATE);
            content.addTo(document);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void addPage(PDDocument document, List<String> paragraphs) throws IOException {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.beginText();
            stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            stream.newLineAtOffset(72, 700);
            for (String paragraph : paragraphs) {
                stream.showText(paragraph);
                stream.newLineAtOffset(0, -36);
            }
            stream.endText();
        }
    }

    private static Calendar fixedDate() {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(2026, Calendar.SEPTEMBER, 24, 9, 0, 0);
        return calendar;
    }
}
