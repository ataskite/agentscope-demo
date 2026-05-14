# P1 Batch Fix Design

Date: 2026-05-14
Status: Approved
Scope: P1-1, P1-2, P1-4, P1-5 (4 issues, P1-3 deferred)

## Context

Code review of agentscope-demo identified 5 P1 issues. P1-3 (API authentication) is deferred per user decision. This spec covers the remaining 4 fixes, executed on branch `fix/p1-batch` as sequential commits grouped by file.

## Fixes

### P1-2: Constructor Injection (ChatController.java:52-65)

Replace 5 `@Autowired` field injections with constructor injection.

- Remove `@Autowired` annotations and make fields `private final`
- Add explicit constructor with all 5 dependencies
- Remove unused `Autowired` import
- Affected file: `ChatController.java` only

### P1-1: File Upload MIME + Magic Bytes Validation (ChatController.java:402-412)

Add content verification after extension check, before file save.

Two layers:
1. **Content-Type check**: `file.getContentType()` must match expected MIME for the extension
2. **Magic bytes check**: Read first 8 bytes of file, match against known signatures

Signature table:
- PDF: `0x25504446` (`%PDF`)
- DOCX/XLSX: `0x504B0304` (ZIP)
- JPEG: `0xFFD8FF`
- PNG: `0x89504E47`
- GIF: `GIF87a`/`GIF89a`
- WAV: `RIFF`
- MP3: `0xFFFB`/`0xFFF3`/`ID3`
- MP4/M4A: `ftyp`

New private methods:
- `getFileHeader(InputStream, int)` — read magic bytes without consuming stream
- `isValidFileSignature(String ext, String contentType, byte[] header)` — validate against signature table

Error response: `"File content does not match its extension"`

### P1-4: Remove Thread.sleep (AgentRuntime.java:262-267)

Delete the `Thread.sleep(100)` try-catch block in `completeStream`. FluxSink guarantees event ordering — no delay needed before emitting "done" and completing.

### P1-5: Wrap Blocking RestTemplate (WebSearchTool.java:25)

Replace direct `restTemplate.postForObject()` call with `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic()).block()`. This executes the blocking HTTP call on the boundedElastic thread pool instead of the Netty event loop.

Method signature and return type (`String`) remain unchanged.

Add import: `reactor.core.publisher.Mono`, `reactor.core.scheduler.Schedulers`

## Execution Order

1. P1-2 (constructor injection) — ChatController.java
2. P1-1 (MIME validation) — ChatController.java
3. P1-4 (Thread.sleep removal) — AgentRuntime.java
4. P1-5 (RestTemplate wrapping) — WebSearchTool.java

## Branch Strategy

Create branch `fix/p1-batch` from current `main`. Each fix is a separate commit.

## Verification

- `mvn clean compile` passes
- Application starts and dependencies inject correctly
- SSE streaming works without blocking
- File upload rejects mismatched content (e.g., .exe renamed to .jpg)
- Web search tool returns results normally

## Output

- Update Feishu Bitable records with fix status, time, and branch
- Generate local fix report
