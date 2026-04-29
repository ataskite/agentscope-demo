package com.skloda.agentscope.service;

import com.skloda.agentscope.model.KnowledgeFileStatus;
import com.skloda.agentscope.model.KnowledgeIndexStatus;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.rag.Knowledge;
import io.agentscope.core.rag.knowledge.SimpleKnowledge;
import io.agentscope.core.rag.model.Document;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.rag.reader.PDFReader;
import io.agentscope.core.rag.reader.ReaderInput;
import io.agentscope.core.rag.reader.TableFormat;
import io.agentscope.core.rag.reader.TextReader;
import io.agentscope.core.rag.reader.WordReader;
import io.agentscope.core.rag.store.InMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.stream.Stream;

/**
 * Manages the RAG knowledge base lifecycle.
 * Uses SimpleKnowledge with InMemoryStore for demo purposes.
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);
    private static final String UPLOADS_PREFIX = "__upload__/";
    private static final String STALE_VECTOR_MESSAGE =
            "previously indexed; InMemoryStore deletion is out of scope, stale vectors may remain";

    private final Knowledge knowledge;
    private final KnowledgeProperties properties;
    private final Map<String, KnowledgeFileStatus> fileStatuses = new LinkedHashMap<>();
    private volatile KnowledgeIndexStatus.State state = KnowledgeIndexStatus.State.EMPTY;
    private volatile OffsetDateTime startedAt;
    private volatile OffsetDateTime finishedAt;

    public KnowledgeService(@Value("${agentscope.model.dashscope.api-key:}") String apiKey,
                            KnowledgeProperties properties) {
        this.properties = properties;
        DashScopeTextEmbedding embeddingModel = DashScopeTextEmbedding.builder()
                .apiKey(apiKey)
                .modelName(properties.getEmbeddingModel())
                .dimensions(properties.getDimensions())
                .build();

        this.knowledge = SimpleKnowledge.builder()
                .embeddingModel(embeddingModel)
                .embeddingStore(InMemoryStore.builder().dimensions(properties.getDimensions()).build())
                .build();

        log.info("KnowledgeService initialized with InMemoryStore (model={}, dimensions={})",
                properties.getEmbeddingModel(), properties.getDimensions());
    }

    /**
     * Add a document file to the knowledge base.
     */
    public synchronized void addDocument(String filePath, String fileName) {
        Path path = Path.of(filePath);
        String extension = extensionOf(fileName);
        String statusKey = uploadStatusKey(fileName);
        startedAt = OffsetDateTime.now();
        finishedAt = null;
        state = KnowledgeIndexStatus.State.INDEXING;

        KnowledgeFileStatus indexingStatus = buildStatus(fileName, statusKey, path, extension,
                KnowledgeFileStatus.Status.INDEXING, 0, null);
        upsertStatus(indexingStatus);
        try {
            if (!isSupportedExtension(extension)) {
                throw new IllegalArgumentException("Unsupported file type: " + fileName);
            }

            List<Document> docs = readDocuments(path, extension);
            int chunkCount = docs == null ? 0 : docs.size();
            if (docs != null && !docs.isEmpty()) {
                knowledge.addDocuments(docs).block();
            }
            upsertStatus(buildStatus(fileName, statusKey, path, extension,
                    KnowledgeFileStatus.Status.INDEXED, chunkCount, null));
            state = determineCompletedState();
            log.info("Indexed {} chunks from: {}", chunkCount, fileName);
        } catch (Exception e) {
            upsertStatus(buildStatus(fileName, statusKey, path, extension,
                    KnowledgeFileStatus.Status.FAILED, 0, e.getMessage()));
            state = determineCompletedState();
            log.error("Failed to index document: {}", fileName, e);
            throw new RuntimeException("Failed to index document: " + e.getMessage(), e);
        } finally {
            finishedAt = OffsetDateTime.now();
        }
    }

    /**
     * Snapshot the local knowledge indexing status.
     */
    public synchronized KnowledgeIndexStatus getIndexStatus() {
        List<KnowledgeFileStatus> sortedDocuments = fileStatuses.values().stream()
                .sorted(Comparator.comparing(KnowledgeFileStatus::getRelativePath,
                        Comparator.nullsLast(String::compareTo)))
                .toList();

        return KnowledgeIndexStatus.builder()
                .state(state)
                .knowledgePath(knowledgePath().toString())
                .startedAt(startedAt)
                .finishedAt(finishedAt)
                .documents(sortedDocuments)
                .build();
    }

    /**
     * Scan and index files from the configured local knowledge directory.
     */
    public synchronized void indexLocalKnowledge() {
        Path root = knowledgePath();
        startedAt = OffsetDateTime.now();
        finishedAt = null;
        state = KnowledgeIndexStatus.State.INDEXING;
        removeTransientLocalStatuses();

        try {
            Files.createDirectories(root);
            List<Path> files;
            try (Stream<Path> paths = Files.walk(root)) {
                files = paths
                        .filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(path -> relativePath(root, path)))
                        .toList();
            }

            for (Path file : files) {
                indexLocalFile(root, file);
            }

            state = determineCompletedState();
        } catch (Exception e) {
            state = KnowledgeIndexStatus.State.FAILED;
            log.error("Failed to scan local knowledge directory: {}", root, e);
        } finally {
            finishedAt = OffsetDateTime.now();
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
    public synchronized List<String> getIndexedDocuments() {
        return fileStatuses.values().stream()
                .filter(status -> status.getStatus() == KnowledgeFileStatus.Status.INDEXED)
                .map(KnowledgeFileStatus::getFileName)
                .toList();
    }

    /**
     * Remove a document from the index list (note: InMemoryStore doesn't support removal).
     */
    public synchronized void removeDocument(String fileName) {
        fileStatuses.entrySet().removeIf(entry -> fileName.equals(entry.getValue().getFileName()));
        state = determineCompletedState();
        log.info("Removed document from index list: {}", fileName);
    }

    private void indexLocalFile(Path root, Path file) {
        String fileName = file.getFileName().toString();
        String relativePath = relativePath(root, file);
        String extension = extensionOf(fileName);

        if (isImageExtension(extension)) {
            upsertStatus(buildStatus(fileName, relativePath, file, extension, KnowledgeFileStatus.Status.SKIPPED,
                    0, "image indexing requires OCR or multimodal embedding"));
            return;
        }

        if (!isSupportedExtension(extension)) {
            upsertStatus(buildStatus(fileName, relativePath, file, extension, KnowledgeFileStatus.Status.SKIPPED,
                    0, "unsupported file type: " + extension));
            return;
        }

        upsertStatus(buildStatus(fileName, relativePath, file, extension, KnowledgeFileStatus.Status.INDEXING,
                0, null));
        try {
            List<Document> docs = readDocuments(file, extension);
            int chunkCount = docs == null ? 0 : docs.size();
            if (docs != null && !docs.isEmpty()) {
                knowledge.addDocuments(docs).block();
            }
            upsertStatus(buildStatus(fileName, relativePath, file, extension, KnowledgeFileStatus.Status.INDEXED,
                    chunkCount, null));
            log.info("Indexed {} chunks from local knowledge file: {}", chunkCount, relativePath);
        } catch (Exception e) {
            upsertStatus(buildStatus(fileName, relativePath, file, extension, KnowledgeFileStatus.Status.FAILED,
                    0, e.getMessage()));
            log.error("Failed to index local knowledge file: {}", file, e);
        }
    }

    private List<Document> readDocuments(Path file, String extension) throws IOException {
        return switch (extension) {
            case "pdf" -> new PDFReader(properties.getChunkSize(), properties.getSplitStrategy(),
                    properties.getOverlapSize())
                    .read(ReaderInput.fromPath(file))
                    .block();
            case "docx", "doc" -> new WordReader(properties.getChunkSize(), properties.getSplitStrategy(),
                    properties.getOverlapSize(), false, false, TableFormat.MARKDOWN)
                    .read(ReaderInput.fromPath(file))
                    .block();
            case "txt", "md" -> new TextReader(properties.getChunkSize(), properties.getSplitStrategy(),
                    properties.getOverlapSize())
                    .read(ReaderInput.fromPath(file))
                    .block();
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }

    private KnowledgeIndexStatus.State determineCompletedState() {
        boolean hasIndexed = fileStatuses.values().stream()
                .anyMatch(status -> status.getStatus() == KnowledgeFileStatus.Status.INDEXED);
        boolean hasFailed = fileStatuses.values().stream()
                .anyMatch(status -> status.getStatus() == KnowledgeFileStatus.Status.FAILED);

        if (hasFailed) {
            return KnowledgeIndexStatus.State.READY_WITH_ERRORS;
        }
        if (hasIndexed) {
            return KnowledgeIndexStatus.State.READY;
        }
        return KnowledgeIndexStatus.State.EMPTY;
    }

    private Path knowledgePath() {
        return Path.of(properties.getPath()).toAbsolutePath().normalize();
    }

    private String relativePath(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private String uploadStatusKey(String fileName) {
        return UPLOADS_PREFIX + fileName;
    }

    private boolean isUploadStatus(KnowledgeFileStatus status) {
        return status.getRelativePath() != null && status.getRelativePath().startsWith(UPLOADS_PREFIX);
    }

    private boolean isSupportedExtension(String extension) {
        return switch (extension) {
            case "md", "txt", "pdf", "docx", "doc" -> true;
            default -> false;
        };
    }

    private boolean isImageExtension(String extension) {
        return switch (extension) {
            case "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff" -> true;
            default -> false;
        };
    }

    private KnowledgeFileStatus buildStatus(String fileName, String relativePath, Path file, String extension,
                                            KnowledgeFileStatus.Status status, int chunkCount, String message) {
        return KnowledgeFileStatus.builder()
                .fileName(fileName)
                .relativePath(relativePath)
                .absolutePath(file.toAbsolutePath().normalize().toString())
                .extension(extension)
                .status(status)
                .chunkCount(chunkCount)
                .message(message)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    private void upsertStatus(KnowledgeFileStatus status) {
        fileStatuses.put(status.getRelativePath(), status);
    }

    private void removeTransientLocalStatuses() {
        List<KnowledgeFileStatus> staleIndexedLocalStatuses = fileStatuses.values().stream()
                .filter(status -> !isUploadStatus(status))
                .filter(status -> status.getStatus() == KnowledgeFileStatus.Status.INDEXED)
                .map(status -> status.toBuilder()
                        .message(STALE_VECTOR_MESSAGE)
                        .updatedAt(OffsetDateTime.now())
                        .build())
                .toList();

        fileStatuses.entrySet().removeIf(entry -> !isUploadStatus(entry.getValue()));
        staleIndexedLocalStatuses.forEach(this::upsertStatus);
    }
}
