package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.policy.service.ParagraphSplitter;
import com.liorshaya.policypilot.support.PdfSamples;
import com.liorshaya.policypilot.web.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PDF text in memory with PDFBox and its limits (Document 5, File and path injection: 2 MB, 50 pages, 10 seconds,
 * no scripts, no embedded files, no OCR; the named tests "a 60-page PDF and a PDF with JavaScript"). The PDFs are
 * built by {@link PdfSamples}.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void textPdfYieldsItsParagraphsInPageOrder() {
        byte[] pdf = PdfSamples.text(List.of(
                List.of("Applicants must be at least 21 years old.", "The loan amount is at most 150,000."),
                List.of("An application meeting all conditions is approved.")));

        List<String> paragraphs = ParagraphSplitter.split(extractor.extract(pdf));

        assertThat(paragraphs).containsExactly(
                "Applicants must be at least 21 years old.",
                "The loan amount is at most 150,000.",
                "An application meeting all conditions is approved.");
    }

    @Test
    void fiftyPagePdfIsAccepted() {
        assertThat(ParagraphSplitter.split(extractor.extract(PdfSamples.pages(50)))).hasSize(50);
    }

    @Test
    void fiftyOnePagePdfIsRejected() {
        assertThatThrownBy(() -> extractor.extract(PdfSamples.pages(51)))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("50 pages"));
    }

    @Test
    void sixtyPagePdfIsRejected() {
        assertThatThrownBy(() -> extractor.extract(PdfSamples.pages(60)))
                .isInstanceOfSatisfying(InputRejectedException.class,
                        e -> assertThat(e.code()).isEqualTo(ErrorCode.UPLOAD_REJECTED));
    }

    @Test
    void pdfWithJavaScriptIsRejected() {
        assertThatThrownBy(() -> extractor.extract(PdfSamples.withJavaScript()))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("JavaScript"));
    }

    @Test
    void pdfWithAnEmbeddedFileIsRejected() {
        assertThatThrownBy(() -> extractor.extract(PdfSamples.withEmbeddedFile()))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("embedded"));
    }

    @Test
    void encryptedPdfIsRejected() {
        assertThatThrownBy(() -> extractor.extract(PdfSamples.encrypted()))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("encrypted"));
    }

    @Test
    void pdfWithoutATextLayerIsRejectedNotOcred() {
        assertThatThrownBy(() -> extractor.extract(PdfSamples.withoutText()))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("no text"));
    }

    @Test
    void aBrokenPdfIsRejected() {
        byte[] broken = "%PDF-1.7\nnot really a pdf".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> extractor.extract(broken))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("readable"));
    }

    @Test
    void extractionOverItsTimeLimitIsAborted() {
        PdfTextExtractor impatient = new PdfTextExtractor(Duration.ofNanos(1));

        assertThatThrownBy(() -> impatient.extract(PdfSamples.pages(50)))
                .isInstanceOfSatisfying(InputRejectedException.class, e -> assertThat(e.problem()).contains("10 seconds"));
    }
}
