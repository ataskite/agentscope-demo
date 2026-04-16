package com.skloda.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class PdfParserTool {

    private static final Logger log = LoggerFactory.getLogger(PdfParserTool.class);

    @Tool(name = "parse_pdf", description = "Parse a PDF file and extract text content from all pages. Returns the extracted text with page numbers and document metadata if available.")
    public String parsePdf(
            @ToolParam(name = "filePath", description = "Absolute path to the PDF file") String filePath) {
        log.info("Parsing PDF file: {}", filePath);
        StringBuilder sb = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(new File(filePath))) {

            PDDocumentInformation info = document.getDocumentInformation();
            if (info != null) {
                String title = info.getTitle();
                String author = info.getAuthor();
                if ((title != null && !title.isBlank()) || (author != null && !author.isBlank())) {
                    sb.append("Document Info:\n");
                    if (title != null && !title.isBlank()) {
                        sb.append("  Title: ").append(title).append("\n");
                    }
                    if (author != null && !author.isBlank()) {
                        sb.append("  Author: ").append(author).append("\n");
                    }
                    sb.append("\n");
                }
            }

            PDFTextStripper stripper = new PDFTextStripper();
            int totalPages = document.getNumberOfPages();

            if (totalPages <= 3) {
                String text = stripper.getText(document);
                sb.append(text);
            } else {
                for (int i = 1; i <= totalPages; i++) {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String pageText = stripper.getText(document);
                    if (pageText != null && !pageText.isBlank()) {
                        sb.append("--- Page ").append(i).append(" ---\n");
                        sb.append(pageText.trim()).append("\n\n");
                    }
                }
            }

            if (sb.isEmpty()) {
                return "The PDF appears to be empty or contains no extractable text (it may be a scanned document).";
            }

            return sb.toString().trim();

        } catch (IOException e) {
            log.error("Failed to parse PDF file: {}", filePath, e);
            return "Error parsing PDF file: " + e.getMessage();
        }
    }
}
