package com.skloda.agentscope.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocxParserToolTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    private Path tempDir;

    private final DocxParserTool tool = new DocxParserTool();

    @Test
    void parseDocxExtractsParagraphAndTableText() throws Exception {
        Path file = createDocx("Hello DOCX", "{{name}}");

        String result = tool.parseDocx(file.toString());

        assertTrue(result.contains("Hello DOCX"));
        assertTrue(result.contains("Key | {{name}}"));
    }

    @Test
    void parseDocxReturnsErrorForMissingFile() {
        String result = tool.parseDocx(tempDir.resolve("missing.docx").toString());

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
        String result = tool.editDocx(tempDir.resolve("missing.docx").toString(),
                "[{\"placeholder\":\"{{name}}\",\"value\":\"张三\"}]");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("Source file not found"));
    }

    private Path createDocx(String paragraphText, String tableValue) throws Exception {
        Path file = tempDir.resolve("sample.docx");
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
