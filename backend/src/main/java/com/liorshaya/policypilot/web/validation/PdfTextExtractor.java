package com.liorshaya.policypilot.web.validation;

import com.liorshaya.policypilot.web.error.ErrorCode;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * The text of an uploaded PDF, read in memory with PDFBox (Document 5, File and path injection: 50 pages, 10 seconds,
 * text only; no scripts, no embedded files, no OCR). A PDF that carries JavaScript, embedded files or encryption is
 * refused rather than read around, and so is one without a text layer. Every refusal is {@code UPLOAD_REJECTED}.
 */
@Component
public class PdfTextExtractor {

    public static final int MAX_PAGES = 50;
    public static final Duration TIME_LIMIT = Duration.ofSeconds(10);

    /** Enough for any policy; a document with more objects than this is not a policy. */
    static final int MAX_OBJECTS = 200_000;

    private static final COSName EMBEDDED_FILES = COSName.getPDFName("EmbeddedFiles");

    private final Duration timeLimit;

    public PdfTextExtractor() {
        this(TIME_LIMIT);
    }

    PdfTextExtractor(Duration timeLimit) {
        this.timeLimit = timeLimit;
    }

    public String extract(byte[] pdf) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> text = executor.submit(() -> read(pdf));
            try {
                return text.get(timeLimit.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                text.cancel(true);
                throw rejected("took longer than 10 seconds to read");
            } catch (ExecutionException e) {
                if (e.getCause() instanceof InputRejectedException rejected) {
                    throw rejected;
                }
                throw rejected("is not a readable PDF");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw rejected("was not read");
            }
        }
    }

    /** Reads the whole document in memory: nothing is written to disk, no temporary file exists. */
    String read(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdf), "",
                IOUtils.createMemoryOnlyStreamCache())) {
            if (document.isEncrypted()) {
                throw rejected("is encrypted");
            }
            if (document.getNumberOfPages() > MAX_PAGES) {
                throw rejected("has more than 50 pages");
            }
            inspect(document.getDocument().getTrailer());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");
            stripper.setParagraphEnd("\n\n");
            stripper.setPageEnd("\n\n");
            String text = stripper.getText(document);
            if (text.isBlank()) {
                throw rejected("has no text layer");
            }
            return text;
        }
    }

    /** Walks every object reachable from the trailer once; refuses JavaScript and embedded files wherever they are. */
    static void inspect(COSDictionary trailer) {
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        pending.push(trailer);
        while (!pending.isEmpty()) {
            COSBase node = pending.pop();
            if (node instanceof COSObject reference) {
                node = reference.getObject();
            }
            if (node == null || !seen.add(node)) {
                continue;
            }
            if (seen.size() > MAX_OBJECTS) {
                throw rejected("has too many objects");
            }
            if (node instanceof COSDictionary dictionary) {
                if (dictionary.containsKey(COSName.JS) || COSName.JAVA_SCRIPT.equals(dictionary.getCOSName(COSName.S))
                        || dictionary.containsKey(COSName.JAVA_SCRIPT)) {
                    throw rejected("contains JavaScript");
                }
                if (dictionary.containsKey(COSName.EF) || dictionary.containsKey(EMBEDDED_FILES)
                        || COSName.EMBEDDED_FILE.equals(dictionary.getCOSName(COSName.TYPE))) {
                    throw rejected("contains an embedded file");
                }
                dictionary.getValues().forEach(pending::push);
            } else if (node instanceof COSArray array) {
                array.forEach(pending::push);
            }
        }
    }

    private static InputRejectedException rejected(String problem) {
        return new InputRejectedException(ErrorCode.UPLOAD_REJECTED, problem);
    }
}
