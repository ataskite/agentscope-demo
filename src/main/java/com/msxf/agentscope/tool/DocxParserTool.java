package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

public class DocxParserTool {

    private static final Logger log = LoggerFactory.getLogger(DocxParserTool.class);

    @Tool(name = "parse_docx", description = "Parse a .docx file and extract its full text content including headings, paragraphs, tables, and lists. Returns the document text with structure preserved.")
    public String parseDocx(
            @ToolParam(name = "filePath", description = "Absolute path to the .docx file") String filePath) {
        log.info("Parsing DOCX file: {}", filePath);
        StringBuilder sb = new StringBuilder();

        try (FileInputStream fis = new FileInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = extractParagraph(paragraph);
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    String tableText = extractTable(table);
                    if (!tableText.isEmpty()) {
                        sb.append(tableText).append("\n\n");
                    }
                }
            }

            if (sb.isEmpty()) {
                return "The document appears to be empty or contains no readable text.";
            }

            return sb.toString().trim();

        } catch (IOException e) {
            log.error("Failed to parse DOCX file: {}", filePath, e);
            return "Error parsing DOCX file: " + e.getMessage();
        }
    }

    private String extractParagraph(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return "";
        }

        String style = paragraph.getStyle();
        if (style != null) {
            switch (style) {
                case "Heading1", "heading 1" -> {
                    return "# " + text;
                }
                case "Heading2", "heading 2" -> {
                    return "## " + text;
                }
                case "Heading3", "heading 3" -> {
                    return "### " + text;
                }
                case "Heading4", "heading 4" -> {
                    return "#### " + text;
                }
            }
        }

        if (paragraph.getNumFmt() != null) {
            int level = paragraph.getNumIlvl() != null ? paragraph.getNumIlvl().intValue() : 0;
            String indent = "  ".repeat(level);
            return indent + "- " + text;
        }

        return text;
    }

    private String extractTable(XWPFTable table) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableRow row : table.getRows()) {
            StringBuilder rowText = new StringBuilder();
            for (int i = 0; i < row.getTableCells().size(); i++) {
                XWPFTableCell cell = row.getTableCells().get(i);
                if (i > 0) {
                    rowText.append(" | ");
                }
                rowText.append(cell.getText().trim());
            }
            sb.append(rowText).append("\n");
        }
        return sb.toString().trim();
    }
}
