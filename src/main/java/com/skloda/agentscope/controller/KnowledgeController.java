package com.skloda.agentscope.controller;

import com.skloda.agentscope.model.KnowledgeIndexStatus;
import com.skloda.agentscope.service.KnowledgeService;
import io.agentscope.core.rag.model.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for knowledge base management.
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    @Autowired
    private KnowledgeService knowledgeService;

    /**
     * Upload and index a document into the knowledge base.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "File name is missing"));
        }

        String lowerName = originalName.toLowerCase();
        if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx")
                && !lowerName.endsWith(".txt") && !lowerName.endsWith(".md")) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Supported formats: .pdf, .docx, .txt, .md"));
        }

        try {
            // Save to temp
            String fileId = UUID.randomUUID().toString();
            String ext = lowerName.substring(lowerName.lastIndexOf('.'));
            Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-knowledge");
            Files.createDirectories(uploadDir);
            Path filePath = uploadDir.resolve(fileId + ext);
            file.transferTo(filePath.toFile());

            // Index
            knowledgeService.addDocument(filePath.toAbsolutePath().toString(), originalName);

            return ResponseEntity.ok(Map.of(
                    "fileName", originalName,
                    "status", "indexed"
            ));
        } catch (Exception e) {
            log.error("Failed to upload knowledge document: {}", originalName, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * List indexed documents.
     */
    @GetMapping("/documents")
    public List<String> listDocuments() {
        return knowledgeService.getIndexedDocuments();
    }

    /**
     * Get current knowledge indexing status.
     */
    @GetMapping("/status")
    public KnowledgeIndexStatus status() {
        return knowledgeService.getIndexStatus();
    }

    /**
     * Test retrieval endpoint.
     */
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody Map<String, Object> body) {
        String query = (String) body.getOrDefault("query", "");
        if (query.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Query is required"));
        }

        int limit = body.containsKey("limit") ? ((Number) body.get("limit")).intValue() : 3;
        double threshold = body.containsKey("threshold") ? ((Number) body.get("threshold")).doubleValue() : 0.5;

        List<Document> results = knowledgeService.retrieve(query, limit, threshold);
        return ResponseEntity.ok(Map.of(
                "query", query,
                "count", results.size(),
                "results", results.stream().map(doc -> {
                    String text = doc.getMetadata() != null ? doc.getMetadata().getContentText() : "";
                    return Map.of(
                            "content", text != null ? text.substring(0, Math.min(200, text.length())) : "",
                            "score", doc.getScore() != null ? doc.getScore().toString() : "N/A"
                    );
                }).toList()
        ));
    }

    /**
     * Remove a document from the knowledge base.
     */
    @DeleteMapping("/documents/{fileName}")
    public ResponseEntity<?> removeDocument(@PathVariable String fileName) {
        knowledgeService.removeDocument(fileName);
        return ResponseEntity.ok(Map.of("removed", true));
    }
}
