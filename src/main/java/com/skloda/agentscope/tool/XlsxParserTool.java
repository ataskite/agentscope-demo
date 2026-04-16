package com.skloda.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

public class XlsxParserTool {

    private static final Logger log = LoggerFactory.getLogger(XlsxParserTool.class);

    @Tool(name = "parse_xlsx", description = "Parse an .xlsx file and extract its content including sheet names, headers, and row data. Returns the spreadsheet content in a readable format.")
    public String parseXlsx(
            @ToolParam(name = "filePath", description = "Absolute path to the .xlsx file") String filePath) {
        log.info("Parsing XLSX file: {}", filePath);
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath);
             XSSFWorkbook workbook = new XSSFWorkbook(fis)) {

            int numberOfSheets = workbook.getNumberOfSheets();
            sb.append("Spreadsheet contains ").append(numberOfSheets).append(" sheet(s).\n\n");

            for (int sheetIndex = 0; sheetIndex < numberOfSheets; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                sb.append("=== Sheet: ").append(sheetName).append(" ===\n");

                DataFormatter formatter = new DataFormatter();

                int firstRow = sheet.getFirstRowNum();
                int lastRow = sheet.getLastRowNum();

                if (lastRow < 0) {
                    sb.append("(Empty sheet)\n\n");
                    continue;
                }

                for (int rowIndex = firstRow; rowIndex <= lastRow; rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null) {
                        continue;
                    }

                    StringBuilder rowText = new StringBuilder();
                    short firstCell = row.getFirstCellNum();
                    short lastCell = row.getLastCellNum();

                    for (int colIndex = firstCell; colIndex < lastCell; colIndex++) {
                        Cell cell = row.getCell(colIndex);
                        String cellValue = "";

                        if (cell != null) {
                            switch (cell.getCellType()) {
                                case STRING -> cellValue = cell.getStringCellValue().trim();
                                case NUMERIC -> {
                                    if (DateUtil.isCellDateFormatted(cell)) {
                                        cellValue = formatter.formatCellValue(cell);
                                    } else {
                                        double numValue = cell.getNumericCellValue();
                                        if (numValue == (long) numValue) {
                                            cellValue = String.valueOf((long) numValue);
                                        } else {
                                            cellValue = String.valueOf(numValue);
                                        }
                                    }
                                }
                                case BOOLEAN -> cellValue = String.valueOf(cell.getBooleanCellValue());
                                case FORMULA -> cellValue = cell.getCellFormula();
                                default -> cellValue = "";
                            }
                        }

                        if (colIndex > firstCell) {
                            rowText.append(" | ");
                        }
                        rowText.append(cellValue.isEmpty() ? "(blank)" : cellValue);
                    }

                    if (rowText.length() > 0) {
                        sb.append(rowText).append("\n");
                    }

                    if (rowIndex == firstRow && lastRow > firstRow + 1) {
                        sb.append("---\n");
                    }
                }
                sb.append("\n");
            }

            if (sb.isEmpty()) {
                return "The spreadsheet appears to be empty.";
            }

            return sb.toString().trim();

        } catch (IOException e) {
            log.error("Failed to parse XLSX file: {}", filePath, e);
            return "Error parsing XLSX file: " + e.getMessage();
        }
    }
}
