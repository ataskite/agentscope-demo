package com.skloda.agentscope.tool;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class XlsxParserToolTest {

    @TempDir
    private Path tempDir;

    private final XlsxParserTool tool = new XlsxParserTool();

    @Test
    void parseXlsxExtractsSheetsRowsAndCellTypes() throws Exception {
        Path file = tempDir.resolve("sample.xlsx");
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
        String result = tool.parseXlsx(tempDir.resolve("missing.xlsx").toString());

        assertTrue(result.startsWith("Error parsing XLSX file: "));
    }
}
