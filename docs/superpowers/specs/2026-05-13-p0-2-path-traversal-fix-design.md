# P0-2 路径穿越漏洞修复设计

## 问题

`ChatController.downloadFile`（lines 441-487）中 `uploadDir.resolve(fileId)` 直接将用户输入拼接到路径，无规范化校验。攻击者可构造 `fileId=../../../etc/passwd` 读取服务器任意文件。

**严重级别**：P0 严重
**维度**：安全
**置信度**：高

## 方案

采用 Java NIO 标准防御：`Path.normalize()` + `startsWith()` 前缀校验。

### 修改文件

`src/main/java/com/skloda/agentscope/controller/ChatController.java`，仅修改 `downloadFile` 方法。

### 改动内容

1. 对 `uploadDir` 和 `resolvedPath` 都调用 `.normalize().toAbsolutePath()`
2. 校验 `resolvedPath.startsWith(normalizedUploadDir)`，不通过返回 400
3. 额外校验 `fileId` 不包含 `..` 片段（双重防御）
4. 文件名前缀匹配逻辑（无扩展名时按前缀查找）保留，对匹配结果同样做前缀校验

### 不改动的内容

- 上传接口 `uploadFile`
- SSE、session、agent 相关端点
- 其他 Controller 方法

### 验证

- 单元测试覆盖：`../etc/passwd`、`..\..\windows\`、`foo/../../etc/shadow` → 全部 400
- 正常 UUID fileId → 200 正常下载
- 非法路径 + 合法路径组合 → 400

## 风险边界

- **影响面**：仅 `downloadFile` 方法，改动约 10 行
- **回归风险**：极低。UUID 格式 fileId（如 `550e8400-e29b-41d4-a716-446655440000.docx`）normalize 后路径不变，不受影响
- **性能影响**：无，`normalize()` 和 `startsWith()` 均为字符串操作

## 工作区隔离

建议在 `fix/p0-2-path-traversal` 分支上修复，使用 worktree 隔离。
