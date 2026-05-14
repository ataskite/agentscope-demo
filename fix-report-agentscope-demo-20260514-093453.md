# P0-2 路径穿越漏洞修复报告

**项目**：agentscope-demo
**修复日期**：2026-05-14
**问题来源**：飞书多维表格「代码审查问题清单 - agentscope-demo」
**修复提交**：`5f4c5c5`

---

## 修复问题

| 字段 | 值 |
|------|-----|
| 问题编号 | P0-2 |
| 严重级别 | P0 严重 |
| 维度 | 安全 |
| 置信度 | 高 |
| 位置 | ChatController.java:449-459 |
| 问题描述 | 文件下载接口存在路径穿越漏洞，fileId 未做路径穿越校验直接传入 resolve() |
| 影响 | 攻击者可读取服务器任意文件 |

## 修复方案

采用 Java NIO 双层防御：

1. **字符串快速拒绝**：`fileId.contains("..")` 在任何文件系统操作前拒绝包含 `..` 的输入
2. **路径规范化 + 前缀校验**：对 `uploadDir` 和 `resolve` 结果都做 `normalize().toAbsolutePath()`，然后校验 `startsWith(normalizedUploadDir)`
3. **fallback 路径同样保护**：`listFiles` 前缀匹配的结果也做 `normalize().toAbsolutePath()` + `startsWith` 校验

## 修改文件

| 文件 | 改动 |
|------|------|
| `src/main/java/com/skloda/agentscope/controller/ChatController.java` | 替换 `downloadFile` 方法，添加路径校验（约 10 行新增逻辑） |
| `src/test/java/com/skloda/agentscope/controller/ChatControllerFileTest.java` | 新增 4 个安全回归测试 |

## 测试覆盖

| 测试方法 | 输入 | 预期 | 实际 |
|----------|------|------|------|
| `downloadFileRejectsPathTraversalWithDotDot` | `../../../etc/passwd` | 400 | PASS |
| `downloadFileRejectsPathTraversalMixed` | `foo/../../etc/shadow` | 400 | PASS |
| `downloadFileRejectsPathTraversalEncoded` | `..\\..\\windows\\system32\\config` | 400 | PASS |
| `downloadFileAcceptsValidUuidFileId` | `550e8400-e29b-41d4-a716-446655440000.docx` | 200 | PASS |

全部 12 个 ChatControllerFileTest 测试通过。全量 348 测试通过（1 个预存在失败与本次修复无关）。

## 代码审查结果

- **Spec 合规审查**：全部 7 项要求通过
- **代码质量审查**：Approved，无安全绕过向量。有 2 个非阻塞建议（`contains("..")` 冗余但无害、fallback listing 使用非规范化路径为理论风险）

## 风险评估

- **影响面**：仅 `downloadFile` 方法
- **回归风险**：极低，UUID 格式 fileId 不受影响
- **性能影响**：无
