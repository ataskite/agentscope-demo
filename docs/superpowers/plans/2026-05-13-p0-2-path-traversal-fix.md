# P0-2 路径穿越漏洞修复 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 ChatController.downloadFile 的路径穿越漏洞，确保 fileId 无法逃逸上传目录。

**Architecture:** 在 `downloadFile` 方法中，对 `uploadDir.resolve(fileId)` 的结果做 `normalize().toAbsolutePath()` 后校验 `startsWith(normalizedUploadDir)`。不通过返回 400。额外校验 `fileId` 不含 `..` 片段作为双重防御。

**Tech Stack:** Java 17, Java NIO (Path/normalize), JUnit 5, @TempDir

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| Modify | `src/main/java/com/skloda/agentscope/controller/ChatController.java:441-487` | 添加路径校验逻辑 |
| Modify | `src/test/java/com/skloda/agentscope/controller/ChatControllerFileTest.java` | 添加路径穿越测试 |

---

### Task 1: 编写路径穿越失败测试

**Files:**
- Modify: `src/test/java/com/skloda/agentscope/controller/ChatControllerFileTest.java`

- [ ] **Step 1: 在 ChatControllerFileTest 中添加路径穿越测试方法**

在 `downloadFileUsesPdfContentTypeForExactFileName` 测试方法之后，添加以下测试：

```java
@Test
void downloadFileRejectsPathTraversalWithDotDot() {
    ResponseEntity<Resource> response = controller.downloadFile("../../../etc/passwd");

    assertEquals(400, response.getStatusCode().value());
}

@Test
void downloadFileRejectsPathTraversalMixed() {
    ResponseEntity<Resource> response = controller.downloadFile("foo/../../etc/shadow");

    assertEquals(400, response.getStatusCode().value());
}

@Test
void downloadFileRejectsPathTraversalEncoded() {
    ResponseEntity<Resource> response = controller.downloadFile("..\\..\\windows\\system32\\config");

    assertEquals(400, response.getStatusCode().value());
}

@Test
void downloadFileAcceptsValidUuidFileId() throws Exception {
    Path uploadDir = tempDir.resolve("agentscope-uploads");
    Files.createDirectories(uploadDir);
    Files.write(uploadDir.resolve("550e8400-e29b-41d4-a716-446655440000.docx"), new byte[] {1, 2, 3});

    ResponseEntity<Resource> response = controller.downloadFile("550e8400-e29b-41d4-a716-446655440000.docx");

    assertEquals(200, response.getStatusCode().value());
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn test -pl . -Dtest=ChatControllerFileTest#downloadFileRejectsPathTraversalWithDotDot -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `../../../etc/passwd` 当前会尝试解析到系统目录并返回 404（file not found），而非 400（bad request）

---

### Task 2: 实现路径校验逻辑

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/controller/ChatController.java:441-487`

- [ ] **Step 1: 替换 downloadFile 方法体**

将 `downloadFile` 方法（lines 441-487）替换为以下实现：

```java
@GetMapping("/chat/download")
@ResponseBody
public ResponseEntity<Resource> downloadFile(@RequestParam String fileId) {
    if (fileId == null || fileId.isBlank()) {
        return ResponseEntity.badRequest().build();
    }

    if (fileId.contains("..")) {
        return ResponseEntity.badRequest().build();
    }

    try {
        Path uploadDir = Paths.get(System.getProperty("java.io.tmpdir"), "agentscope-uploads");
        Path normalizedUploadDir = uploadDir.normalize().toAbsolutePath();
        Path filePath = normalizedUploadDir.resolve(fileId).normalize().toAbsolutePath();

        if (!filePath.startsWith(normalizedUploadDir)) {
            return ResponseEntity.badRequest().build();
        }

        if (!filePath.toFile().exists() && !fileId.contains(".")) {
            File[] matchingFiles = uploadDir.toFile().listFiles((dir, name) ->
                    name.startsWith(fileId) && (name.endsWith(".docx") || name.endsWith(".pdf") || name.endsWith(".xlsx"))
            );
            if (matchingFiles != null && matchingFiles.length > 0) {
                filePath = matchingFiles[0].toPath().normalize().toAbsolutePath();
                if (!filePath.startsWith(normalizedUploadDir)) {
                    return ResponseEntity.badRequest().build();
                }
            }
        }

        File file = filePath.toFile();
        if (!file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }

        String fileName = file.getName();
        String contentType = "application/octet-stream";
        if (fileName.endsWith(".docx")) {
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (fileName.endsWith(".pdf")) {
            contentType = "application/pdf";
        } else if (fileName.endsWith(".xlsx")) {
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        Resource resource = new FileSystemResource(file);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(resource);

    } catch (Exception e) {
        log.error("Failed to download file: {}", fileId, e);
        return ResponseEntity.internalServerError().build();
    }
}
```

关键改动点：
1. 在 blank 检查之后增加 `fileId.contains("..")` 快速拒绝（双重防御第一层）
2. `uploadDir` 先 `normalize().toAbsolutePath()` 得到规范基路径
3. `resolve` 后也做 `normalize().toAbsolutePath()`，再 `startsWith` 校验（第二层）
4. 前缀匹配结果的路径同样做 `normalize().toAbsolutePath()` + `startsWith` 校验

- [ ] **Step 2: 运行全部文件测试确认通过**

Run: `mvn test -pl . -Dtest=ChatControllerFileTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 全部 PASS（包括原有的 8 个测试 + 新增的 4 个测试）

- [ ] **Step 3: 运行全量测试确认无回归**

Run: `mvn test`
Expected: BUILD SUCCESS

---

### Task 3: 提交修复

- [ ] **Step 1: 提交改动**

```bash
git add src/main/java/com/skloda/agentscope/controller/ChatController.java src/test/java/com/skloda/agentscope/controller/ChatControllerFileTest.java
git commit -m "fix(security): prevent path traversal in file download endpoint (P0-2)

Add path normalization and prefix validation to downloadFile method.
Reject fileIds containing '..' fragments. Verify resolved path stays
within the upload directory after normalize().toAbsolutePath()."
```

---

### Task 4: 验证修复效果

- [ ] **Step 1: 编译并启动应用**

Run: `mvn spring-boot:run`

- [ ] **Step 2: 手动验证路径穿越被阻止**

在另一个终端执行：

```bash
# 应返回 400 Bad Request
curl -v "http://localhost:8080/chat/download?fileId=../../../etc/passwd"

# 应返回 400 Bad Request
curl -v "http://localhost:8080/chat/download?fileId=foo/../../etc/shadow"
```

Expected: 两次请求都返回 400 状态码

---

### Task 5: 生成修复报告

- [ ] **Step 1: 生成本地 Markdown 修复报告**

创建文件 `fix-report-agentscope-demo-<timestamp>.md`，内容包括：
- 修复问题：P0-2
- 修复方案：normalize + startsWith 前缀校验
- 修改文件列表
- 测试覆盖
- 验证结果

- [ ] **Step 2: 提交报告**

```bash
git add fix-report-agentscope-demo-*.md
git commit -m "docs: add P0-2 fix report"
```
