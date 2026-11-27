package com.skloda.agentscope.tool;

import com.skloda.agentscope.security.UploadPathGuard;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fixtures live inside the upload whitelist directory (P1-2).
 */
class XlsxParserToolTest {

    private final XlsxParserTool tool = new XlsxParserTool();
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
    void parseXlsxExtractsSheetsRowsAndCellTypes() throws Exception {
        Path file = whitelistPath("sample-" + System.nanoTime() + ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("Data");
            var header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Amount");
            header.createCell(2).setCellValue("Active");
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("Alice");
            row.createCell(1).setCellValue(42);
            row.createCell(2).setCellValue(true);
            row.createCell(3).setCellFormula("B2*2");
            try (OutputStream out = Files.newOutputStream(file)) {
                workbook.write(out);
            }
        }

        String result = tool.parseXlsx(file.toString());

        assertTrue(result.contains("Spreadsheet contains 1 sheet(s)."));
        assertTrue(result.contains("=== Sheet: Data ==="));
        assertTrue(result.contains("Name | Amount | Active"));
        assertTrue(result.contains("Alice | 42 | true | B2*2"));
    }

    @Test
    void parseXlsxReturnsErrorForMissingFile() {
        String result = tool.parseXlsx(whitelistPath("missing-" + System.nanoTime() + ".xlsx").toString());

        assertTrue(result.startsWith("Error parsing XLSX file: "));
    }
}
