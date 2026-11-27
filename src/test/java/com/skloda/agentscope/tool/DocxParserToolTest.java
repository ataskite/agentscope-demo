package com.skloda.agentscope.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skloda.agentscope.security.UploadPathGuard;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures live inside the upload whitelist directory (P1-2): tool filePath params
 * are only honored inside {java.io.tmpdir}/agentscope-uploads.
 */
class DocxParserToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DocxParserTool tool = new DocxParserTool();
    private final java.util.List<String> fixtures = new java.util.ArrayList<>();

    private Path whitelistPath(String name) {
        fixtures.add(name);
        return UploadPathGuard.uploadDir().resolve(name);
    }

    @AfterEach
    void cleanup() throws Exception {
        for (String name : fixtures) {
            Files.deleteIfExists(UploadPathGuard.uploadDir().resolve(name.replace(".docx", "_edited.docx")));
            Files.deleteIfExists(UploadPathGuard.uploadDir().resolve(name));
        }
        fixtures.clear();
    }

    @Test
    void parseDocxExtractsParagraphAndTableText() throws Exception {
        Path file = createDocx("Hello DOCX", "{{name}}");

        String result = tool.parseDocx(file.toString());

        assertTrue(result.contains("Hello DOCX"));
        assertTrue(result.contains("Key | {{name}}"));
    }

    @Test
    void parseDocxReturnsErrorForMissingFile() {
        // Missing but INSIDE the whitelist: exercises the file-not-found branch,
        // not the whitelist rejection
        String result = tool.parseDocx(whitelistPath("missing-" + System.nanoTime() + ".docx").toString());

        assertTrue(result.startsWith("Error parsing DOCX file: "));
    }

    @Test
    void editDocxReplacesPlaceholderAndWritesNewFile() throws Exception {
        Path file = createDocx("Customer: {{name}}", "Table {{name}}");

        String result = tool.editDocx(file.toString(), "[{\"placeholder\":\"{{name}}\",\"value\":\"张三\"}]");
        JsonNode json = MAPPER.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertEquals(2, json.get("replacementsMade").asInt());
        assertTrue(Files.exists(Path.of(json.get("outputFilePath").asText())));
    }

    @Test
    void editDocxRejectsEmptyReplacements() throws Exception {
        Path file = createDocx("Hello", "World");

        String result = tool.editDocx(file.toString(), "[]");

        assertEquals("{\"success\":false,\"message\":\"No replacements provided\"}", result);
    }

    @Test
    void editDocxReportsMissingSourceFile() {
        String result = tool.editDocx(whitelistPath("missing-" + System.nanoTime() + ".docx").toString(),
                "[{\"placeholder\":\"{{name}}\",\"value\":\"张三\"}]");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("Source file not found"));
    }

    private Path createDocx(String paragraphText, String tableValue) throws Exception {
        Path file = whitelistPath("sample-" + System.nanoTime() + ".docx");
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(paragraphText);

            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Key");
            table.getRow(0).getCell(1).setText(tableValue);

            try (OutputStream out = Files.newOutputStream(file)) {
                document.write(out);
            }
        }
        return file;
    }
}
