# Generic RAG Local Knowledge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `rag-chat` to AgentScope Generic RAG backed by startup background indexing of local files in `knowledge/`.

**Architecture:** `KnowledgeService` owns `SimpleKnowledge`, index status, reader selection, and background indexing. `AgentFactory` maps agent config to AgentScope `RAGMode.GENERIC`. `KnowledgeController` exposes status, and the agent config modal polls and displays the knowledge index state for RAG-enabled agents.

**Tech Stack:** Spring Boot 3.5.13, Java 17, AgentScope Java 1.0.11, Reactor, Apache POI/PDFBox through AgentScope readers, vanilla JS frontend.

---

## File Structure

- Create `src/main/java/com/skloda/agentscope/model/KnowledgeFileStatus.java`: per-file status DTO.
- Create `src/main/java/com/skloda/agentscope/model/KnowledgeIndexStatus.java`: aggregate status DTO returned by the API.
- Create `src/main/java/com/skloda/agentscope/service/KnowledgeProperties.java`: typed config for `agentscope.knowledge`.
- Modify `src/main/java/com/skloda/agentscope/AgentScopeDemoApplication.java`: enable configuration properties.
- Modify `src/main/java/com/skloda/agentscope/service/KnowledgeService.java`: add status tracking, local-directory indexing, reader routing, and async startup trigger.
- Modify `src/main/java/com/skloda/agentscope/controller/KnowledgeController.java`: add `GET /api/knowledge/status`.
- Modify `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`: add `ragMode`.
- Modify `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`: map config `ragMode` to AgentScope `RAGMode`.
- Modify `src/main/resources/config/agents.yml`: set `ragMode: generic` on `rag-chat`.
- Modify `src/main/resources/application.yml`: add knowledge path and indexing settings.
- Modify `src/main/resources/static/scripts/api.js`: add `fetchKnowledgeStatus`.
- Modify `src/main/resources/static/scripts/modules/agents.js`: display knowledge status in agent config modal and poll while indexing.
- Modify `src/main/resources/static/styles/modules/modal.css`: style knowledge status block.
- Create tests:
  - `src/test/java/com/skloda/agentscope/model/KnowledgeIndexStatusTest.java`
  - `src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java`
  - `src/test/java/com/skloda/agentscope/controller/KnowledgeControllerTest.java`
  - extend `src/test/java/com/skloda/agentscope/agent/AgentConfigTest.java`

## Task 1: Add Knowledge Status DTOs

**Files:**
- Create: `src/main/java/com/skloda/agentscope/model/KnowledgeFileStatus.java`
- Create: `src/main/java/com/skloda/agentscope/model/KnowledgeIndexStatus.java`
- Test: `src/test/java/com/skloda/agentscope/model/KnowledgeIndexStatusTest.java`

- [ ] **Step 1: Write DTO tests**

```java
package com.skloda.agentscope.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeIndexStatusTest {

    @Test
    void createsStatusSnapshotWithCounts() {
        KnowledgeFileStatus indexed = KnowledgeFileStatus.builder()
                .fileName("guide.md")
                .relativePath("guide.md")
                .extension("md")
                .status(KnowledgeFileStatus.Status.INDEXED)
                .chunkCount(3)
                .updatedAt(OffsetDateTime.parse("2026-04-29T13:00:00+08:00"))
                .build();
        KnowledgeFileStatus skipped = KnowledgeFileStatus.builder()
                .fileName("diagram.png")
                .relativePath("diagram.png")
                .extension("png")
                .status(KnowledgeFileStatus.Status.SKIPPED)
                .chunkCount(0)
                .message("unsupported file type")
                .updatedAt(OffsetDateTime.parse("2026-04-29T13:00:01+08:00"))
                .build();

        KnowledgeIndexStatus status = KnowledgeIndexStatus.builder()
                .state(KnowledgeIndexStatus.State.READY)
                .knowledgePath("/repo/knowledge")
                .startedAt(OffsetDateTime.parse("2026-04-29T13:00:00+08:00"))
                .finishedAt(OffsetDateTime.parse("2026-04-29T13:00:02+08:00"))
                .documents(List.of(indexed, skipped))
                .build();

        assertEquals(KnowledgeIndexStatus.State.READY, status.getState());
        assertEquals(2, status.getTotalFiles());
        assertEquals(1, status.getIndexedFiles());
        assertEquals(1, status.getSkippedFiles());
        assertEquals(0, status.getFailedFiles());
        assertTrue(status.getDocuments().contains(indexed));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeIndexStatusTest test`

Expected: compile failure because `KnowledgeFileStatus` and `KnowledgeIndexStatus` do not exist.

- [ ] **Step 3: Create `KnowledgeFileStatus`**

```java
package com.skloda.agentscope.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder(toBuilder = true)
public class KnowledgeFileStatus {

    public enum Status {
        PENDING,
        INDEXING,
        INDEXED,
        SKIPPED,
        FAILED
    }

    String fileName;
    String relativePath;
    String absolutePath;
    String extension;
    Status status;
    int chunkCount;
    String message;
    OffsetDateTime updatedAt;
}
```

- [ ] **Step 4: Create `KnowledgeIndexStatus`**

```java
package com.skloda.agentscope.model;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;
import java.util.List;

@Value
@Builder(toBuilder = true)
public class KnowledgeIndexStatus {

    public enum State {
        EMPTY,
        PENDING,
        INDEXING,
        READY,
        READY_WITH_ERRORS,
        FAILED
    }

    State state;
    String knowledgePath;
    int totalFiles;
    int indexedFiles;
    int skippedFiles;
    int failedFiles;
    OffsetDateTime startedAt;
    OffsetDateTime finishedAt;
    List<KnowledgeFileStatus> documents;

    public static class KnowledgeIndexStatusBuilder {
        public KnowledgeIndexStatus build() {
            List<KnowledgeFileStatus> safeDocuments =
                    documents == null ? List.of() : List.copyOf(documents);
            totalFiles = safeDocuments.size();
            indexedFiles = (int) safeDocuments.stream()
                    .filter(doc -> doc.getStatus() == KnowledgeFileStatus.Status.INDEXED)
                    .count();
            skippedFiles = (int) safeDocuments.stream()
                    .filter(doc -> doc.getStatus() == KnowledgeFileStatus.Status.SKIPPED)
                    .count();
            failedFiles = (int) safeDocuments.stream()
                    .filter(doc -> doc.getStatus() == KnowledgeFileStatus.Status.FAILED)
                    .count();
            documents = safeDocuments;
            return new KnowledgeIndexStatus(state, knowledgePath, totalFiles, indexedFiles,
                    skippedFiles, failedFiles, startedAt, finishedAt, documents);
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn -Dtest=KnowledgeIndexStatusTest test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/skloda/agentscope/model/KnowledgeFileStatus.java \
  src/main/java/com/skloda/agentscope/model/KnowledgeIndexStatus.java \
  src/test/java/com/skloda/agentscope/model/KnowledgeIndexStatusTest.java
git commit -m "feat: add knowledge index status models"
```

## Task 2: Add Knowledge Configuration Properties

**Files:**
- Create: `src/main/java/com/skloda/agentscope/service/KnowledgeProperties.java`
- Modify: `src/main/java/com/skloda/agentscope/AgentScopeDemoApplication.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java`

- [ ] **Step 1: Write configuration default test**

Add this test class:

```java
package com.skloda.agentscope.service;

import io.agentscope.core.rag.reader.SplitStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceStatusTest {

    @Test
    void knowledgePropertiesUseDocumentedDefaults() {
        KnowledgeProperties properties = new KnowledgeProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.isAutoIndexOnStartup());
        assertEquals("knowledge", properties.getPath());
        assertEquals("text-embedding-v3", properties.getEmbeddingModel());
        assertEquals(1024, properties.getDimensions());
        assertEquals(512, properties.getChunkSize());
        assertEquals(50, properties.getOverlapSize());
        assertEquals(SplitStrategy.PARAGRAPH, properties.getSplitStrategy());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeServiceStatusTest test`

Expected: compile failure because `KnowledgeProperties` does not exist.

- [ ] **Step 3: Create `KnowledgeProperties`**

```java
package com.skloda.agentscope.service;

import io.agentscope.core.rag.reader.SplitStrategy;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "agentscope.knowledge")
public class KnowledgeProperties {
    private boolean enabled = true;
    private String path = "knowledge";
    private String embeddingModel = "text-embedding-v3";
    private int dimensions = 1024;
    private int chunkSize = 512;
    private int overlapSize = 50;
    private SplitStrategy splitStrategy = SplitStrategy.PARAGRAPH;
    private boolean autoIndexOnStartup = true;
}
```

- [ ] **Step 4: Enable configuration properties**

Modify `src/main/java/com/skloda/agentscope/AgentScopeDemoApplication.java` by adding the `KnowledgeProperties` import and `@EnableConfigurationProperties` annotation while preserving the existing startup banner:

```java
package com.skloda.agentscope;

import com.skloda.agentscope.service.KnowledgeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KnowledgeProperties.class)
public class AgentScopeDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentScopeDemoApplication.class, args);
        System.out.println("""

                ================================================
                AgentScope Demo Application Started!

                Open http://localhost:8080 in your browser.
                ================================================
                """);
    }
}
```

- [ ] **Step 5: Extend `application.yml`**

In `src/main/resources/application.yml`, replace the existing knowledge block with:

```yaml
  # Knowledge Configuration
  knowledge:
    enabled: true
    path: knowledge
    embedding-model: text-embedding-v3
    dimensions: 1024
    chunk-size: 512
    overlap-size: 50
    split-strategy: PARAGRAPH
    auto-index-on-startup: true
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn -Dtest=KnowledgeServiceStatusTest test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/skloda/agentscope/service/KnowledgeProperties.java \
  src/main/java/com/skloda/agentscope/AgentScopeDemoApplication.java \
  src/main/resources/application.yml \
  src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java
git commit -m "feat: add knowledge indexing configuration"
```

## Task 3: Implement Local Knowledge Indexing Status

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/service/KnowledgeService.java`
- Test: `src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java`

- [ ] **Step 1: Add classification tests**

Extend `KnowledgeServiceStatusTest` with:

```java
package com.skloda.agentscope.service;

import com.skloda.agentscope.model.KnowledgeFileStatus;
import com.skloda.agentscope.model.KnowledgeIndexStatus;
import io.agentscope.core.rag.reader.SplitStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeServiceStatusTest {

    @TempDir
    Path tempDir;

    @Test
    void knowledgePropertiesUseDocumentedDefaults() {
        KnowledgeProperties properties = new KnowledgeProperties();

        assertTrue(properties.isEnabled());
        assertTrue(properties.isAutoIndexOnStartup());
        assertEquals("knowledge", properties.getPath());
        assertEquals("text-embedding-v3", properties.getEmbeddingModel());
        assertEquals(1024, properties.getDimensions());
        assertEquals(512, properties.getChunkSize());
        assertEquals(50, properties.getOverlapSize());
        assertEquals(SplitStrategy.PARAGRAPH, properties.getSplitStrategy());
    }

    @Test
    void scanMarksEmptyDirectoryAsEmpty() throws Exception {
        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setPath(tempDir.resolve("knowledge").toString());
        KnowledgeService service = new KnowledgeService("", properties);

        service.indexLocalKnowledge();

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.EMPTY, status.getState());
        assertEquals(0, status.getTotalFiles());
    }

    @Test
    void scanSkipsImageFilesWithoutFailingIndex() throws Exception {
        Path knowledgeDir = tempDir.resolve("knowledge");
        Files.createDirectories(knowledgeDir);
        Files.writeString(knowledgeDir.resolve("diagram.png"), "fake image bytes");

        KnowledgeProperties properties = new KnowledgeProperties();
        properties.setPath(knowledgeDir.toString());
        KnowledgeService service = new KnowledgeService("", properties);

        service.indexLocalKnowledge();

        KnowledgeIndexStatus status = service.getIndexStatus();
        assertEquals(KnowledgeIndexStatus.State.EMPTY, status.getState());
        assertEquals(1, status.getTotalFiles());
        assertEquals(1, status.getSkippedFiles());
        assertEquals(KnowledgeFileStatus.Status.SKIPPED, status.getDocuments().get(0).getStatus());
        assertTrue(status.getDocuments().get(0).getMessage().contains("image indexing requires OCR"));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -Dtest=KnowledgeServiceStatusTest test`

Expected: compile failure because the constructor and methods are not implemented yet.

- [ ] **Step 3: Refactor constructor and status fields**

In `KnowledgeService`, replace constructor fields with:

```java
private final Knowledge knowledge;
private final KnowledgeProperties properties;
private final List<KnowledgeFileStatus> fileStatuses = new CopyOnWriteArrayList<>();
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

    log.info("KnowledgeService initialized with InMemoryStore (dimensions={})", properties.getDimensions());
}
```

Add imports:

```java
import com.skloda.agentscope.model.KnowledgeFileStatus;
import com.skloda.agentscope.model.KnowledgeIndexStatus;
import io.agentscope.core.rag.reader.TableFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
```

- [ ] **Step 4: Add status snapshot and local scan methods**

Add these methods to `KnowledgeService`:

```java
private static final Set<String> IMAGE_EXTENSIONS = Set.of(
        "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff");

public KnowledgeIndexStatus getIndexStatus() {
    return KnowledgeIndexStatus.builder()
            .state(state)
            .knowledgePath(resolveKnowledgePath().toString())
            .startedAt(startedAt)
            .finishedAt(finishedAt)
            .documents(fileStatuses.stream()
                    .sorted(Comparator.comparing(KnowledgeFileStatus::getRelativePath))
                    .toList())
            .build();
}

public void indexLocalKnowledge() {
    startedAt = OffsetDateTime.now();
    finishedAt = null;
    fileStatuses.clear();
    state = KnowledgeIndexStatus.State.INDEXING;

    Path knowledgePath = resolveKnowledgePath();
    try {
        Files.createDirectories(knowledgePath);
        List<Path> files;
        try (var stream = Files.walk(knowledgePath)) {
            files = stream.filter(Files::isRegularFile)
                    .sorted()
                    .toList();
        }
        if (files.isEmpty()) {
            state = KnowledgeIndexStatus.State.EMPTY;
            finishedAt = OffsetDateTime.now();
            return;
        }
        for (Path file : files) {
            indexDiscoveredFile(knowledgePath, file);
        }
        boolean hasIndexed = fileStatuses.stream()
                .anyMatch(status -> status.getStatus() == KnowledgeFileStatus.Status.INDEXED);
        boolean hasFailed = fileStatuses.stream()
                .anyMatch(status -> status.getStatus() == KnowledgeFileStatus.Status.FAILED);
        if (!hasIndexed) {
            state = hasFailed ? KnowledgeIndexStatus.State.READY_WITH_ERRORS : KnowledgeIndexStatus.State.EMPTY;
        } else {
            state = hasFailed ? KnowledgeIndexStatus.State.READY_WITH_ERRORS : KnowledgeIndexStatus.State.READY;
        }
    } catch (Exception e) {
        state = KnowledgeIndexStatus.State.FAILED;
        log.error("Failed to index local knowledge directory", e);
    } finally {
        finishedAt = OffsetDateTime.now();
    }
}

private Path resolveKnowledgePath() {
    Path path = Paths.get(properties.getPath());
    return path.isAbsolute() ? path : Paths.get("").toAbsolutePath().resolve(path).normalize();
}
```

- [ ] **Step 5: Add per-file routing**

Add these methods to `KnowledgeService`:

```java
private void indexDiscoveredFile(Path root, Path file) {
    String extension = extensionOf(file);
    String relativePath = root.relativize(file).toString();
    if (IMAGE_EXTENSIONS.contains(extension)) {
        fileStatuses.add(status(file, relativePath, extension, KnowledgeFileStatus.Status.SKIPPED,
                0, "unsupported file type: image indexing requires OCR or multimodal embedding"));
        return;
    }
    if (!isSupported(extension)) {
        fileStatuses.add(status(file, relativePath, extension, KnowledgeFileStatus.Status.SKIPPED,
                0, "unsupported file type: " + extension));
        return;
    }

    fileStatuses.add(status(file, relativePath, extension, KnowledgeFileStatus.Status.INDEXING, 0, null));
    try {
        List<Document> docs = readDocuments(file, extension);
        if (docs != null && !docs.isEmpty()) {
            knowledge.addDocuments(docs).block();
        }
        replaceStatus(relativePath, status(file, relativePath, extension,
                KnowledgeFileStatus.Status.INDEXED, docs == null ? 0 : docs.size(), null));
        log.info("Indexed {} chunks from local knowledge file: {}", docs == null ? 0 : docs.size(), relativePath);
    } catch (Exception e) {
        replaceStatus(relativePath, status(file, relativePath, extension,
                KnowledgeFileStatus.Status.FAILED, 0, e.getMessage()));
        log.warn("Failed to index local knowledge file: {}", relativePath, e);
    }
}

private List<Document> readDocuments(Path file, String extension) throws IOException {
    return switch (extension) {
        case "pdf" -> new PDFReader(properties.getChunkSize(), properties.getSplitStrategy(),
                properties.getOverlapSize()).read(ReaderInput.fromPath(file)).block();
        case "doc", "docx" -> new WordReader(properties.getChunkSize(), properties.getSplitStrategy(),
                properties.getOverlapSize(), false, false, TableFormat.MARKDOWN)
                .read(ReaderInput.fromPath(file)).block();
        case "txt", "md" -> new TextReader(properties.getChunkSize(), properties.getSplitStrategy(),
                properties.getOverlapSize()).read(ReaderInput.fromPath(file)).block();
        default -> List.of();
    };
}

private boolean isSupported(String extension) {
    return Set.of("pdf", "doc", "docx", "txt", "md").contains(extension);
}

private String extensionOf(Path file) {
    String fileName = file.getFileName().toString();
    int dot = fileName.lastIndexOf('.');
    if (dot < 0 || dot == fileName.length() - 1) {
        return "";
    }
    return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
}

private KnowledgeFileStatus status(Path file, String relativePath, String extension,
                                   KnowledgeFileStatus.Status status, int chunkCount, String message) {
    return KnowledgeFileStatus.builder()
            .fileName(file.getFileName().toString())
            .relativePath(relativePath)
            .absolutePath(file.toAbsolutePath().toString())
            .extension(extension)
            .status(status)
            .chunkCount(chunkCount)
            .message(message)
            .updatedAt(OffsetDateTime.now())
            .build();
}

private void replaceStatus(String relativePath, KnowledgeFileStatus replacement) {
    fileStatuses.removeIf(status -> relativePath.equals(status.getRelativePath()));
    fileStatuses.add(replacement);
}
```

- [ ] **Step 6: Update existing methods to use status documents**

Replace `getIndexedDocuments()` with:

```java
public List<String> getIndexedDocuments() {
    return fileStatuses.stream()
            .filter(status -> status.getStatus() == KnowledgeFileStatus.Status.INDEXED)
            .map(KnowledgeFileStatus::getFileName)
            .toList();
}
```

Replace `removeDocument(String fileName)` with:

```java
public void removeDocument(String fileName) {
    fileStatuses.removeIf(status -> fileName.equals(status.getFileName()));
    log.info("Removed document from index list: {}", fileName);
}
```

Keep `addDocument`, `retrieve`, and `getKnowledge`, but update `addDocument` reader construction to use `properties`.

- [ ] **Step 7: Run tests**

Run: `mvn -Dtest=KnowledgeServiceStatusTest test`

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/skloda/agentscope/service/KnowledgeService.java \
  src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java
git commit -m "feat: track local knowledge indexing status"
```

## Task 4: Trigger Background Startup Indexing

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/service/KnowledgeService.java`
- Test: `src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java`

- [ ] **Step 1: Add async trigger test**

Add this test:

```java
@Test
void startupIndexingIsSkippedWhenDisabled() {
    KnowledgeProperties properties = new KnowledgeProperties();
    properties.setEnabled(false);
    KnowledgeService service = new KnowledgeService("", properties);

    service.startBackgroundIndexing();

    assertEquals(KnowledgeIndexStatus.State.EMPTY, service.getIndexStatus().getState());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeServiceStatusTest#startupIndexingIsSkippedWhenDisabled test`

Expected: compile failure because `startBackgroundIndexing` does not exist.

- [ ] **Step 3: Add application-ready listener and background executor**

Add imports to `KnowledgeService`:

```java
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
```

Add field:

```java
private final ExecutorService indexingExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread thread = new Thread(r, "knowledge-indexer");
    thread.setDaemon(true);
    return thread;
});
```

Add methods:

```java
@EventListener(ApplicationReadyEvent.class)
public void onApplicationReady() {
    startBackgroundIndexing();
}

public void startBackgroundIndexing() {
    if (!properties.isEnabled() || !properties.isAutoIndexOnStartup()) {
        log.info("Knowledge auto-indexing skipped (enabled={}, autoIndexOnStartup={})",
                properties.isEnabled(), properties.isAutoIndexOnStartup());
        return;
    }
    indexingExecutor.submit(this::indexLocalKnowledge);
}
```

- [ ] **Step 4: Run test**

Run: `mvn -Dtest=KnowledgeServiceStatusTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/service/KnowledgeService.java \
  src/test/java/com/skloda/agentscope/service/KnowledgeServiceStatusTest.java
git commit -m "feat: index local knowledge after startup"
```

## Task 5: Wire Generic RAG Mode From Agent Config

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java`
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java`
- Modify: `src/main/resources/config/agents.yml`
- Test: `src/test/java/com/skloda/agentscope/agent/AgentConfigTest.java`

- [ ] **Step 1: Add config tests**

Extend `AgentConfigTest`:

```java
@Test
void testDefaultRagModeIsGeneric() {
    AgentConfig config = new AgentConfig();

    assertEquals("generic", config.getRagMode());
}

@Test
void testRagModeCanBeSet() {
    AgentConfig config = new AgentConfig();
    config.setRagMode("agentic");

    assertEquals("agentic", config.getRagMode());
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=AgentConfigTest test`

Expected: compile failure because `ragMode` does not exist.

- [ ] **Step 3: Add `ragMode` to `AgentConfig`**

In `AgentConfig`, add under RAG settings:

```java
private String ragMode = "generic";
```

- [ ] **Step 4: Map string to `RAGMode` in `AgentFactory`**

Replace the current hardcoded `RAGMode.AGENTIC` block with:

```java
if (config.isRagEnabled()) {
    RAGMode ragMode = parseRagMode(config.getRagMode());
    builder.knowledge(knowledgeService.getKnowledge())
            .ragMode(ragMode)
            .retrieveConfig(RetrieveConfig.builder()
                    .limit(config.getRagRetrieveLimit())
                    .scoreThreshold(config.getRagScoreThreshold())
                    .build());
    log.info("  Enabled RAG for agent: {} (mode={}, limit={}, threshold={})",
            agentId, ragMode, config.getRagRetrieveLimit(), config.getRagScoreThreshold());
}
```

Add helper method:

```java
private RAGMode parseRagMode(String value) {
    if (value == null || value.isBlank()) {
        return RAGMode.GENERIC;
    }
    return switch (value.trim().toLowerCase()) {
        case "generic" -> RAGMode.GENERIC;
        case "agentic" -> RAGMode.AGENTIC;
        case "none" -> RAGMode.NONE;
        default -> {
            log.warn("Unknown RAG mode '{}', falling back to GENERIC", value);
            yield RAGMode.GENERIC;
        }
    };
}
```

- [ ] **Step 5: Update `rag-chat` config**

In `src/main/resources/config/agents.yml`, under `rag-chat` add:

```yaml
    ragMode: generic
```

Leave `ragRetrieveLimit: 5` and `ragScoreThreshold: 0.3` unchanged.

- [ ] **Step 6: Run test**

Run: `mvn -Dtest=AgentConfigTest test`

Expected: PASS.

- [ ] **Step 7: Compile**

Run: `mvn clean compile`

Expected: BUILD SUCCESS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java \
  src/main/java/com/skloda/agentscope/agent/AgentFactory.java \
  src/main/resources/config/agents.yml \
  src/test/java/com/skloda/agentscope/agent/AgentConfigTest.java
git commit -m "feat: configure rag chat with generic rag mode"
```

## Task 6: Add Knowledge Status API

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/controller/KnowledgeController.java`
- Test: `src/test/java/com/skloda/agentscope/controller/KnowledgeControllerTest.java`

- [ ] **Step 1: Write controller test**

Create `KnowledgeControllerTest`:

```java
package com.skloda.agentscope.controller;

import com.skloda.agentscope.model.KnowledgeFileStatus;
import com.skloda.agentscope.model.KnowledgeIndexStatus;
import com.skloda.agentscope.service.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeControllerTest {

    @Mock
    private KnowledgeService knowledgeService;

    @InjectMocks
    private KnowledgeController controller;

    @Test
    void statusReturnsCurrentKnowledgeStatus() {
        KnowledgeIndexStatus expected = KnowledgeIndexStatus.builder()
                .state(KnowledgeIndexStatus.State.READY)
                .knowledgePath("/repo/knowledge")
                .startedAt(OffsetDateTime.parse("2026-04-29T13:00:00+08:00"))
                .finishedAt(OffsetDateTime.parse("2026-04-29T13:00:01+08:00"))
                .documents(List.of(KnowledgeFileStatus.builder()
                        .fileName("guide.md")
                        .relativePath("guide.md")
                        .extension("md")
                        .status(KnowledgeFileStatus.Status.INDEXED)
                        .chunkCount(4)
                        .updatedAt(OffsetDateTime.parse("2026-04-29T13:00:01+08:00"))
                        .build()))
                .build();
        when(knowledgeService.getIndexStatus()).thenReturn(expected);

        KnowledgeIndexStatus actual = controller.status();

        assertEquals(expected, actual);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -Dtest=KnowledgeControllerTest test`

Expected: compile failure because `status()` does not exist.

- [ ] **Step 3: Add status endpoint**

In `KnowledgeController`, add import:

```java
import com.skloda.agentscope.model.KnowledgeIndexStatus;
```

Add method:

```java
@GetMapping("/status")
public KnowledgeIndexStatus status() {
    return knowledgeService.getIndexStatus();
}
```

- [ ] **Step 4: Run controller test**

Run: `mvn -Dtest=KnowledgeControllerTest test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/controller/KnowledgeController.java \
  src/test/java/com/skloda/agentscope/controller/KnowledgeControllerTest.java
git commit -m "feat: expose knowledge indexing status"
```

## Task 7: Show Knowledge Status In Agent Config Modal

**Files:**
- Modify: `src/main/resources/static/scripts/api.js`
- Modify: `src/main/resources/static/scripts/modules/agents.js`
- Modify: `src/main/resources/static/styles/modules/modal.css`

- [ ] **Step 1: Add API function**

In `src/main/resources/static/scripts/api.js`, add under knowledge API:

```javascript
export async function fetchKnowledgeStatus() {
    var response = await fetch('/api/knowledge/status');
    return await response.json();
}
```

- [ ] **Step 2: Import API function**

Change the import in `src/main/resources/static/scripts/modules/agents.js` to:

```javascript
import { fetchAgents, fetchSkillInfo, fetchToolInfo, fetchKnowledgeStatus } from '../api.js';
```

- [ ] **Step 3: Add modal placeholder for RAG agents**

In `showAgentConfig`, before the System Prompt divider, add this HTML fragment to the modal body when `config.ragEnabled` is true:

```javascript
var knowledgeHtml = '';
if (config.ragEnabled) {
    knowledgeHtml =
        '<hr class="config-divider">' +
        '<div class="config-field knowledge-status-field">' +
            '<div class="config-field-label">Knowledge</div>' +
            '<div class="knowledge-status" id="knowledgeStatus">Loading...</div>' +
        '</div>';
}
```

Then include `knowledgeHtml +` before the System Prompt section:

```javascript
                knowledgeHtml +
                '<hr class="config-divider">' +
                '<div class="config-field">' +
                    '<div class="config-field-label">System Prompt</div>' +
                    '<div class="config-field-value mono">' + escapeHtml(config.systemPrompt || '') + '</div>' +
                '</div>' +
```

After `document.body.appendChild(overlay);`, add:

```javascript
    if (config.ragEnabled) {
        startKnowledgeStatusPolling();
    }
```

- [ ] **Step 4: Add rendering and polling helpers**

Add these functions in `agents.js` near the config viewer helpers:

```javascript
var knowledgeStatusTimer = null;

async function startKnowledgeStatusPolling() {
    stopKnowledgeStatusPolling();
    await refreshKnowledgeStatus();
    knowledgeStatusTimer = setInterval(refreshKnowledgeStatus, 2500);
}

function stopKnowledgeStatusPolling() {
    if (knowledgeStatusTimer) {
        clearInterval(knowledgeStatusTimer);
        knowledgeStatusTimer = null;
    }
}

async function refreshKnowledgeStatus() {
    var target = document.getElementById('knowledgeStatus');
    if (!target) {
        stopKnowledgeStatusPolling();
        return;
    }
    try {
        var status = await fetchKnowledgeStatus();
        target.innerHTML = renderKnowledgeStatus(status);
        if (['READY', 'READY_WITH_ERRORS', 'EMPTY', 'FAILED'].indexOf(status.state) >= 0) {
            stopKnowledgeStatusPolling();
        }
    } catch (err) {
        target.innerHTML = '<div class="knowledge-status-error">Failed to load knowledge status</div>';
        stopKnowledgeStatusPolling();
    }
}

function renderKnowledgeStatus(status) {
    var documents = status.documents || [];
    var rows = documents.map(function(doc) {
        return '<div class="knowledge-status-row">' +
            '<span class="knowledge-status-file">' + escapeHtml(doc.relativePath || doc.fileName || '') + '</span>' +
            '<span class="knowledge-status-badge ' + escapeHtml(String(doc.status || '').toLowerCase()) + '">' +
                escapeHtml(doc.status || 'UNKNOWN') +
            '</span>' +
            '<span class="knowledge-status-chunks">' + Number(doc.chunkCount || 0) + ' chunks</span>' +
            (doc.message ? '<span class="knowledge-status-message">' + escapeHtml(doc.message) + '</span>' : '') +
            '</div>';
    }).join('');
    if (rows === '') {
        rows = '<div class="knowledge-status-empty">No knowledge files indexed</div>';
    }
    return '<div class="knowledge-status-summary">' +
            '<span class="knowledge-status-state">' + escapeHtml(status.state || 'UNKNOWN') + '</span>' +
            '<span>' + Number(status.indexedFiles || 0) + ' indexed</span>' +
            '<span>' + Number(status.skippedFiles || 0) + ' skipped</span>' +
            '<span>' + Number(status.failedFiles || 0) + ' failed</span>' +
        '</div>' +
        '<div class="knowledge-status-path">' + escapeHtml(status.knowledgePath || '') + '</div>' +
        '<div class="knowledge-status-list">' + rows + '</div>';
}
```

Update `window.closeConfigModal`:

```javascript
window.closeConfigModal = function() {
    stopKnowledgeStatusPolling();
    var modal = document.getElementById('configModal');
    if (modal) modal.remove();
};
```

- [ ] **Step 5: Add CSS**

Add to `src/main/resources/static/styles/modules/modal.css`:

```css
.knowledge-status {
    display: flex;
    flex-direction: column;
    gap: 10px;
}

.knowledge-status-summary {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    font-size: 12px;
    color: var(--text-secondary);
}

.knowledge-status-state,
.knowledge-status-badge {
    border: 1px solid var(--border-color);
    border-radius: 6px;
    padding: 2px 8px;
    color: var(--text-primary);
}

.knowledge-status-path {
    font-family: var(--font-mono);
    font-size: 12px;
    color: var(--text-secondary);
    word-break: break-all;
}

.knowledge-status-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
    max-height: 220px;
    overflow: auto;
}

.knowledge-status-row {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto auto;
    gap: 8px;
    align-items: center;
    font-size: 12px;
}

.knowledge-status-file {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

.knowledge-status-message {
    grid-column: 1 / -1;
    color: var(--text-secondary);
}

.knowledge-status-error {
    color: var(--error-color);
}
```

- [ ] **Step 6: Run frontend smoke check through compile**

Run: `mvn clean compile`

Expected: BUILD SUCCESS.

- [ ] **Step 7: Commit**

```bash
git add src/main/resources/static/scripts/api.js \
  src/main/resources/static/scripts/modules/agents.js \
  src/main/resources/static/styles/modules/modal.css
git commit -m "feat: show knowledge status in agent config"
```

## Task 8: Full Verification

**Files:**
- Verify all changed files.
- Optionally create local untracked sample files under `knowledge/` for manual testing and remove them before final status.

- [ ] **Step 1: Run targeted tests**

Run:

```bash
mvn -Dtest=KnowledgeIndexStatusTest,KnowledgeServiceStatusTest,KnowledgeControllerTest,AgentConfigTest test
```

Expected: BUILD SUCCESS.

- [ ] **Step 2: Run full test suite**

Run:

```bash
mvn test
```

Expected: BUILD SUCCESS. If unrelated pre-existing tests fail, capture exact failing test names and error messages in the final handoff.

- [ ] **Step 3: Compile package surface**

Run:

```bash
mvn clean compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 4: Manual startup check**

Create a local sample directory if it does not already exist:

```bash
mkdir -p knowledge
printf 'AgentScope Generic RAG smoke document.\n' > knowledge/rag-smoke.md
printf 'fake image' > knowledge/skipped-image.png
```

Run:

```bash
DASHSCOPE_API_KEY="${DASHSCOPE_API_KEY:-}" mvn spring-boot:run
```

Expected:

- Application starts before local document indexing logs complete.
- Logs include local knowledge indexing activity.
- `/api/knowledge/status` returns at least one indexed `.md` file and one skipped `.png` file.

- [ ] **Step 5: Cleanup manual samples if they were created only for testing**

Run:

```bash
rm -f knowledge/rag-smoke.md knowledge/skipped-image.png
rmdir knowledge 2>/dev/null || true
```

- [ ] **Step 6: Final git status**

Run:

```bash
git status --short
```

Expected: only pre-existing unrelated changes remain, or clean if this branch had no other work.

## Plan Self-Review

Spec coverage:

- Generic mode is implemented in Task 5.
- Startup background indexing is implemented in Task 4.
- Local `knowledge/` scanning and supported reader mapping are implemented in Task 3.
- Image skip behavior is implemented in Task 3.
- Status API is implemented in Task 6.
- Agent config modal display and polling are implemented in Task 7.
- Configuration defaults are implemented in Task 2.
- Verification is covered by Task 8.

No unresolved placeholders are intentionally left in this plan. Type names are consistent across model, service, controller, and frontend tasks.
