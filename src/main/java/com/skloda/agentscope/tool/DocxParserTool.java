package com.skloda.agentscope.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class DocxParserTool {

    private static final Logger log = LoggerFactory.getLogger(DocxParserTool.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static record ReplacementRecord(String placeholder, String value) {}

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

    @Tool(name = "edit_docx", description = "Edit variables in a .docx file by replacing placeholder text with values. Saves to a new file with '_edited' suffix.")
    public String editDocx(
            @ToolParam(name = "filePath", description = "Absolute path to the source .docx file") String filePath,
            @ToolParam(name = "replacements", description = "JSON array of replacement objects: [{\"placeholder\":\"{{name}}\",\"value\":\"张三\"}]") String replacementsJson) {

        log.info("Editing DOCX file: {}", filePath);

        try {
            // Parse replacements JSON
            List<ReplacementRecord> replacements = objectMapper.readValue(
                replacementsJson,
                new TypeReference<List<ReplacementRecord>>() {}
            );

            if (replacements == null || replacements.isEmpty()) {
                return "{\"success\":false,\"message\":\"No replacements provided\"}";
            }

            // Load the document
            File sourceFile = new File(filePath);
            if (!sourceFile.exists()) {
                return "{\"success\":false,\"message\":\"Source file not found: " + filePath + "\"}";
            }

            try (FileInputStream fis = new FileInputStream(filePath);
                 XWPFDocument document = new XWPFDocument(fis)) {

                int replacementsMade = 0;

                // Replace in paragraphs
                for (XWPFParagraph paragraph : document.getParagraphs()) {
                    replacementsMade += replaceInParagraph(paragraph, replacements);
                }

                // Replace in tables
                for (XWPFTable table : document.getTables()) {
                    for (XWPFTableRow row : table.getRows()) {
                        for (XWPFTableCell cell : row.getTableCells()) {
                            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                                replacementsMade += replaceInParagraph(paragraph, replacements);
                            }
                        }
                    }
                }

                // Generate output file path
                String originalPath = filePath.substring(0, filePath.lastIndexOf('.'));
                String outputPath = originalPath + "_edited.docx";
                File outputFile = new File(outputPath);

                // Save edited document
                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    document.write(fos);
                }

                log.info("DOCX edit complete: {} replacements -> {}", replacementsMade, outputPath);

                // Return result as JSON
                return String.format(
                    "{\"success\":true,\"outputFilePath\":\"%s\",\"replacementsMade\":%d,\"message\":\"Successfully replaced %d placeholders\"}",
                    outputPath, replacementsMade, replacementsMade
                );

            }
        } catch (Exception e) {
            log.error("Failed to edit DOCX file: {}", filePath, e);
            return "{\"success\":false,\"message\":\"Error editing DOCX: " + e.getMessage() + "\"}";
        }
    }

    private int replaceInParagraph(XWPFParagraph paragraph, List<ReplacementRecord> replacements) {
        int count = 0;
        String paragraphText = paragraph.getText();

        if (paragraphText == null || paragraphText.isEmpty()) {
            return 0;
        }

        // Check if any replacement matches
        for (ReplacementRecord replacement : replacements) {
            if (paragraphText.contains(replacement.placeholder())) {
                // Replace all runs in this paragraph
                String newText = paragraphText.replace(replacement.placeholder(), replacement.value());

                // Clear existing runs and add new text
                for (int i = paragraph.getRuns().size() - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }

                XWPFRun newRun = paragraph.createRun();
                newRun.setText(newText);
                count++;
                break; // Only apply one replacement per paragraph to avoid conflicts
            }
        }
        return count;
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
