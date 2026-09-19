package com.liorshaya.policypilot.web.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.liorshaya.policypilot.support.PdfSamples;
import com.liorshaya.policypilot.web.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Upload type by magic bytes, never by name (Document 5, File and path injection: "type is checked by magic bytes,
 * not extension"; Uploads: .txt, .md, .pdf). The reader never sees a file name: the API discards it.
 */
class UploadReaderTest {

    private final UploadReader reader = new UploadReader(new PdfTextExtractor());

    @Test
    void pdfIsRecognizedByItsMagicBytes() {
        byte[] pdf = PdfSamples.text(List.of(List.of("Applicants must be at least 21 years old.")));

        assertThat(reader.read(pdf)).contains("Applicants must be at least 21 years old.");
    }

    @Test
    void utf8TextIsAcceptedAsTextOrMarkdown() {
        String markdown = "# מדיניות\n\nהלוואה אישית תינתן ליחיד שגילו 21 עד 70 בעת הגשת הבקשה.\n";

        assertThat(reader.read(markdown.getBytes(StandardCharsets.UTF_8))).isEqualTo(markdown);
    }

    // Expected: the PE header of a Windows executable starts with "MZ" and carries NUL bytes (Microsoft PE format)
    @Test
    void renamedExeIsRejected() {
        byte[] exe = {'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00};

        assertThatThrownBy(() -> reader.read(exe)).isInstanceOfSatisfying(InputRejectedException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.UPLOAD_REJECTED));
    }

    static List<byte[]> otherBinaries() {
        return List.of(
                new byte[] {'P', 'K', 0x03, 0x04, 0x14, 0x00},
                new byte[] {0x7f, 'E', 'L', 'F', 0x02, 0x01, 0x01, 0x00},
                new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
    }

    // Expected: the ZIP, ELF and PNG signatures
    @ParameterizedTest
    @MethodSource("otherBinaries")
    void zipElfAndPngAreRejected(byte[] binary) {
        assertThatThrownBy(() -> reader.read(binary)).isInstanceOf(InputRejectedException.class);
    }

    @Test
    void invalidUtf8IsRejected() {
        byte[] latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);

        assertThatThrownBy(() -> reader.read(latin1)).isInstanceOf(InputRejectedException.class);
    }

    @Test
    void anEmptyFileIsEmptyText() {
        assertThat(reader.read(new byte[0])).isEmpty();
    }

    @Test
    void aFileShorterThanThePdfSignatureIsText() {
        assertThat(reader.read("%PD".getBytes(StandardCharsets.US_ASCII))).isEqualTo("%PD");
    }
}
