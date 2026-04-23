package com.skloda.agentscope.service;

import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.SplitStrategy;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.agentscope.core.rag.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages the RAG knowledge base lifecycle.
 * Uses SimpleKnowledge with InMemoryStore for demo purposes.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final Knowledge knowledge;
    private final List<String> indexedDocuments = new CopyOnWriteArrayList<>();

    public KnowledgeService(@Value("${agentscope.model.dashscope.api-key:}") String apiKey,
                            @Value("${agentscope.knowledge.dimensions:1024}") int dimensions) {
        DashScopeTextEmbedding embeddingModel = DashScopeTextEmbedding.builder()
                .apiKey(apiKey)
                .modelName("text-embedding-v3")
                .dimensions(dimensions)
                .build();

        this.knowledge = SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(InMemoryStore.builder().dimensions(dimensions).build())
                .build();

        log.info("KnowledgeService initialized with InMemoryStore (dimensions={})", dimensions);
    }

    /**
     * Add a document file to the knowledge base.
     */
    public void addDocument(String filePath, String fileName) {
        try {
            String lowerName = fileName.toLowerCase();
            List<Document> docs;

            if (lowerName.endsWith(".pdf")) {
                PDFReader reader = new PDFReader(512, SplitStrategy.PARAGRAPH, 50);
                docs = reader.read(ReaderInput.fromPath(Path.of(filePath))).block();
            } else if (lowerName.endsWith(".docx") || lowerName.endsWith(".doc")) {
                WordReader reader = new WordReader(512, SplitStrategy.PARAGRAPH, 50, false, false,
                        io.agentscope.core.rag.reader.TableFormat.MARKDOWN);
                docs = reader.read(ReaderInput.fromPath(Path.of(filePath))).block();
            } else if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
                TextReader reader = new TextReader(512, SplitStrategy.PARAGRAPH, 50);
                docs = reader.read(ReaderInput.fromPath(Path.of(filePath))).block();
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + fileName);
            }

            if (docs != null && !docs.isEmpty()) {
                knowledge.addDocuments(docs).block();
                indexedDocuments.add(fileName);
                log.info("Indexed {} chunks from: {}", docs.size(), fileName);
            }
        } catch (Exception e) {
            log.error("Failed to index document: {}", fileName, e);
            throw new RuntimeException("Failed to index document: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve relevant documents from the knowledge base.
     */
    public List<Document> retrieve(String query, int limit, double threshold) {
        try {
            return knowledge.retrieve(query,
                    RetrieveConfig.builder()
                            .limit(limit)
                            .scoreThreshold(threshold)
                            .build())
                    .block();
        } catch (Exception e) {
            log.error("Failed to retrieve documents for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get the underlying Knowledge instance for agent injection.
     */
    public Knowledge getKnowledge() {
        return knowledge;
    }

    /**
     * List indexed document names.
     */
    public List<String> getIndexedDocuments() {
        return new ArrayList<>(indexedDocuments);
    }

    /**
     * Remove a document from the index list (note: InMemoryStore doesn't support removal).
     */
    public void removeDocument(String fileName) {
        indexedDocuments.remove(fileName);
        log.info("Removed document from index list: {}", fileName);
    }
}
