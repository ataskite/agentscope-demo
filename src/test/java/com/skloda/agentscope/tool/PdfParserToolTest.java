package com.skloda.agentscope.tool;

import com.skloda.agentscope.security.UploadPathGuard;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures live inside the upload whitelist directory (P1-2).
 */
class PdfParserToolTest {

    private final PdfParserTool tool = new PdfParserTool();
    private final java.util.List<String> fixtures = new java.util.ArrayList<>();

    private Path whitelistPath(String name) {
        fixtures.add(name);
        return UploadPathGuard.uploadDir().resolve(name);
    }

    @AfterEach
    void cleanup() throws Exception {
        for (String name : fixtures) {
            Files.deleteIfExists(UploadPathGuard.uploadDir().resolve(name));
        }
        fixtures.clear();
    }

    @Test
    void parsePdfReportsEmptyOrScannedPdfWhenNoTextIsExtractable() throws Exception {
        Path file = whitelistPath("empty-" + System.nanoTime() + ".pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(file.toFile());
        }

        assertEquals("The PDF appears to be empty or contains no extractable text (it may be a scanned document).",
                tool.parsePdf(file.toString()));
    }

    @Test
    void parsePdfReturnsErrorForMissingFile() {
        String result = tool.parsePdf(whitelistPath("missing-" + System.nanoTime() + ".pdf").toString());

        assertTrue(result.startsWith("Error parsing PDF file: "));
    }
}
