# Generic RAG Local Knowledge Design

## Context

The project already has a `rag-chat` agent, `KnowledgeService`, and `SimpleKnowledge` backed by `InMemoryStore`. The current implementation indexes documents mainly through the upload API and wires RAG with `RAGMode.AGENTIC`.

The target behavior is to follow the AgentScope Java RAG documentation more closely:

- Use `SimpleKnowledge` for local knowledge.
- Use `RAGMode.GENERIC` so retrieval is automatic before reasoning.
- Load local files from the project `knowledge/` directory.
- Index in the background after application startup so Spring container startup is not delayed.
- Expose current knowledge indexing status in the RAG agent configuration area.

## Goals

- Keep application startup fast.
- Automatically index supported files under `knowledge/` shortly after startup.
- Make `rag-chat` use AgentScope Generic RAG.
- Show users what knowledge has been loaded and whether indexing is still running.
- Treat unsupported image files as skipped, not errors.

## Non-Goals

- No image OCR or multimodal image embedding in this iteration.
- No persistent vector database migration.
- No filesystem watcher for live add/update/delete.
- No reliable deletion from `InMemoryStore`, because the current store does not expose a suitable remove workflow.

## Selected Approach

Use startup background asynchronous indexing plus Generic RAG.

Spring should finish container startup normally. After the application is ready, a background task scans `${agentscope.knowledge.path:knowledge}`, reads supported documents with AgentScope readers, and adds chunks to the existing `SimpleKnowledge`.

The RAG agent can be used while indexing is in progress. It retrieves from whatever content is already indexed. If nothing relevant has been indexed yet, it should answer according to the existing system prompt and avoid inventing knowledge-base facts.

## Backend Design

### KnowledgeService

`KnowledgeService` remains the owner of the `SimpleKnowledge` instance, but its constructor only creates lightweight objects:

- `DashScopeTextEmbedding`
- `InMemoryStore`
- `SimpleKnowledge`
- in-memory status tracking structures

It must not scan or parse files in the constructor.

### Startup Indexing

Add a startup hook, preferably `ApplicationReadyEvent`, that triggers asynchronous indexing when:

- `agentscope.knowledge.enabled=true`
- `agentscope.knowledge.auto-index-on-startup=true`

Indexing should run on a background executor, not on the main Spring startup thread.

If the configured knowledge directory does not exist, create it and mark global status as `EMPTY`.

### Supported Files

Supported local knowledge files:

- `.md`
- `.txt`
- `.pdf`
- `.docx`
- `.doc`

Reader mapping:

- `.md` and `.txt`: `TextReader`
- `.pdf`: `PDFReader`
- `.docx` and `.doc`: `WordReader`

Reader settings come from configuration:

- chunk size
- overlap size
- split strategy

Default values should match the current implementation and AgentScope examples:

- `chunk-size: 512`
- `overlap-size: 50`
- `split-strategy: PARAGRAPH`

For Word files, use table extraction with Markdown format. Image extraction should not be relied on in this iteration because the embedding model is text-only.

### Skipped Files

Image files should be tracked as skipped:

- `.png`
- `.jpg`
- `.jpeg`
- `.webp`
- `.gif`
- `.bmp`
- `.tiff`

Skipped image status message:

`unsupported file type: image indexing requires OCR or multimodal embedding`

Other unsupported extensions should also be skipped with an unsupported file type message.

### Status Model

Maintain a thread-safe status snapshot in memory.

Global states:

- `EMPTY`: knowledge directory exists but no indexable files were found.
- `PENDING`: files have been discovered but indexing has not started.
- `INDEXING`: background indexing is running.
- `READY`: indexing finished with no failed supported files.
- `READY_WITH_ERRORS`: indexing finished and at least one supported file failed.
- `FAILED`: an unexpected whole-indexing failure prevented the scan from completing.

Per-file states:

- `PENDING`
- `INDEXING`
- `INDEXED`
- `SKIPPED`
- `FAILED`

Per-file metadata:

- file name
- relative path from `knowledge/`
- absolute path if useful for debugging
- extension
- status
- chunk count
- message or error summary
- updated timestamp

Failures in one file must not stop other files from indexing.

### RAG Agent Wiring

Change RAG wiring in `AgentFactory` from `RAGMode.AGENTIC` to `RAGMode.GENERIC`.

Preferred implementation: add a config field `ragMode` to `AgentConfig`, defaulting to `generic`, so future agents can choose `generic`, `agentic`, or `none`.

For `rag-chat`, configure:

```yaml
ragEnabled: true
ragMode: generic
ragRetrieveLimit: 5
ragScoreThreshold: 0.3
```

`AgentFactory` should map this value to AgentScope `RAGMode` and continue using the existing `RetrieveConfig`.

## API Design

Add a new endpoint:

`GET /api/knowledge/status`

Response shape:

```json
{
  "state": "INDEXING",
  "knowledgePath": "/Users/jiangkun/Documents/workspace/agentscope-demo/knowledge",
  "totalFiles": 8,
  "indexedFiles": 5,
  "skippedFiles": 2,
  "failedFiles": 1,
  "startedAt": "2026-04-29T13:00:00+08:00",
  "finishedAt": null,
  "documents": [
    {
      "fileName": "guide.md",
      "relativePath": "guide.md",
      "extension": "md",
      "status": "INDEXED",
      "chunkCount": 12,
      "message": null,
      "updatedAt": "2026-04-29T13:00:03+08:00"
    },
    {
      "fileName": "diagram.png",
      "relativePath": "diagram.png",
      "extension": "png",
      "status": "SKIPPED",
      "chunkCount": 0,
      "message": "unsupported file type: image indexing requires OCR or multimodal embedding",
      "updatedAt": "2026-04-29T13:00:03+08:00"
    }
  ]
}
```

Keep existing upload and search APIs for now. Upload can continue indexing into the same `SimpleKnowledge`. The new local-directory indexer is the main path for this iteration.

## Frontend Design

In the agent configuration/details area, show a Knowledge status block when the selected agent has `ragEnabled=true`.

Display:

- global state: `Ready`, `Indexing`, `Ready with errors`, `Empty`, or `Failed`
- configured knowledge path
- indexed, skipped, failed, and total counts
- document list with file name, status, chunk count, and message

Polling behavior:

- Fetch `/api/knowledge/status` when the page loads.
- Fetch again when selecting an RAG-enabled agent.
- Poll every 2-3 seconds while state is `PENDING` or `INDEXING`.
- Stop polling when state is `READY`, `READY_WITH_ERRORS`, `EMPTY`, or `FAILED`.

The UI should make skipped images visible but not alarming.

## Configuration

Extend `application.yml`:

```yaml
agentscope:
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

The path is relative to the working directory unless an absolute path is provided.

## Testing Plan

Java tests:

- `KnowledgeService` handles empty directory and reports `EMPTY`.
- `KnowledgeService` classifies supported files, skipped image files, and unsupported files.
- `KnowledgeService` continues indexing after a single file failure.
- `KnowledgeController` returns the aggregate status and document list from `/api/knowledge/status`.
- `AgentFactory` uses `RAGMode.GENERIC` for `ragEnabled=true` with `ragMode=generic`.

Verification:

- Run `mvn test` if feasible.
- At minimum run targeted tests plus `mvn clean compile`.

Manual check:

- Add sample `.md`, `.txt`, `.pdf`, and image files under `knowledge/`.
- Start the app.
- Confirm startup is not blocked by document parsing.
- Confirm UI shows indexing progress and final file list.
- Ask `rag-chat` a question covered by a local document and verify the answer uses retrieved knowledge.

## Open Decisions Resolved

- Indexing trigger: application ready event, background asynchronous task.
- RAG mode: Generic.
- Local knowledge path: `knowledge/` by default.
- Image handling: skip images and show them in status; no OCR or multimodal embedding for this iteration.
- Filesystem watching: out of scope.
