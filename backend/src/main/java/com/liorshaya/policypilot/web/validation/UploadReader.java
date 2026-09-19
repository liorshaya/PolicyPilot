package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.web.error.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

/**
 * Reads an uploaded policy file (Document 5, Uploads: {@code .txt}, {@code .md}, {@code .pdf}, 2 MB, one file): the
 * type is decided by the bytes, never by the name, which is discarded. {@code %PDF-} is a PDF, read as text by
 * {@link PdfTextExtractor}; valid UTF-8 without NUL is text or Markdown; anything else, an executable renamed to
 * {@code .md} included, is {@code UPLOAD_REJECTED}. The text then follows the policy text rules.
 */
@Component
public class UploadReader {

    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    private final PdfTextExtractor pdf;

    public UploadReader(PdfTextExtractor pdf) {
        this.pdf = pdf;
    }

    public String read(byte[] bytes) {
        if (startsWith(bytes, PDF)) {
            return pdf.extract(bytes);
        }
        for (byte b : bytes) {
            if (b == 0) {
                throw rejected();
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw rejected();
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static InputRejectedException rejected() {
        return new InputRejectedException(ErrorCode.UPLOAD_REJECTED, "is neither a PDF nor UTF-8 text");
    }
}
