# P2 代码质量问题修复设计

**日期**: 2026-05-14
**项目**: agentscope-demo
**范围**: P2-1 到 P2-5 五个代码质量问题的修复

## 概述

本设计文档描述了对 agentscope-demo 项目中 5 个 P2 级别代码质量问题的修复方案。这些问题涉及性能优化、安全改进、代码质量和资源管理等方面。

## 修复范围

| 问题ID | 严重级别 | 类别 | 描述 |
|--------|----------|------|------|
| P2-1 | P2 一般 | 性能 | PipelineAgentRuntime 中使用 .block() 阻塞调用 |
| P2-2 | P2 一般 | 安全 | ChatController 上传响应暴露服务器绝对路径 |
| P2-3 | P2 一般 | 代码质量 | CompositeAgentFactory 中 Routing 和 Handoffs 创建逻辑重复 |
| P2-4 | P2 一般 | 资源管理 | ObjectMapper 在多个 Runtime 类中重复创建 |
| P2-5 | P2 一般 | 技术债 | 诊断工具类 CorrectSkillDiagnostic 残留在生产源码中 |

## 架构设计

### 修复架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        修复范围                              │
├─────────────────────────────────────────────────────────────┤
│  P2-5: 移除诊断类            P2-4: ObjectMapper配置        │
│  ↓                        ↓                                  │
│  删除文件                  新增JacksonConfig Bean           │
│                                                            │
│  P2-2: 移除绝对路径         P2-1: 移除.block()              │
│  ↓                        ↓                                  │
│  修改ChatController        修改PipelineAgentRuntime         │
│  只返回fileId              使用Mono.fromCallable             │
│                                                            │
│  P2-3: 提取公共方法                                            │
│  ↓                                                            │
│  在CompositeAgentFactory中提取私有方法                            │
└─────────────────────────────────────────────────────────────┘
```

## 详细设计

### 1. P2-5：移除诊断类

**位置**: `src/main/java/com/skloda/agentscope/config/CorrectSkillDiagnostic.java`

**问题**: 临时诊断类位于 src/main/java，会增加生产打包体积

**解决方案**:
- 删除 `CorrectSkillDiagnostic.java` 文件
- 该类已有 `@Profile("correct-skill-diagnostic")` 注解，不会在生产环境激活

**影响范围**:
- 仅影响开发调试功能
- 生产环境无影响

### 2. P2-4：共享 ObjectMapper 实例

**位置**:
- 新增: `src/main/java/com/skloda/agentscope/config/JacksonConfig.java`
- 修改: `ChatController.java`, `AgentRuntime.java`, `PipelineAgentRuntime.java`

**问题**: 多个类各自创建静态 ObjectMapper，浪费内存且配置可能不一致

**解决方案**:
1. 创建 JacksonConfig 配置类，声明 ObjectMapper Bean
2. 修改各 Runtime 类，移除静态 ObjectMapper，改为注入使用
3. ChatController 已使用实例变量，保持不变

**代码示例**:

```java
@Configuration
public class JacksonConfig {
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

### 3. P2-2：移除绝对路径暴露

**位置**: `ChatController.java:439`

**问题**: 文件上传响应返回服务器绝对路径，存在信息泄露风险

**解决方案**:
- 修改 `uploadFile` 方法响应，移除 `filePath` 字段
- 只返回 `fileId`, `fileName`, `fileType`

**修改前**:
```java
return Map.of(
    "fileId", fileId,
    "fileName", originalName,
    "filePath", filePath.toAbsolutePath().toString(),  // 移除
    "fileType", isImage ? "image" : isAudio ? "audio" : "document"
);
```

**修改后**:
```java
return Map.of(
    "fileId", fileId,
    "fileName", originalName,
    "fileType", isImage ? "image" : isAudio ? "audio" : "document"
);
```

### 4. P2-1：移除 .block() 阻塞

**位置**: `PipelineAgentRuntime.java:66`

**问题**: 在响应式流中使用阻塞的 `.block()` 调用，影响并发性能

**解决方案**:
- 使用 `Mono.fromCallable()` + `subscribeOn(Schedulers.boundedElastic())` 包装阻塞调用
- 将阻塞操作移到弹性调度器上执行

**修改前**:
```java
Object result = pipeline.execute(userMsg).block();
```

**修改后**:
```java
Object result = Mono.fromCallable(() -> pipeline.execute(userMsg))
    .subscribeOn(Schedulers.boundedElastic())
    .block();
```

### 5. P2-3：提取公共方法

**位置**: `CompositeAgentFactory.java`

**问题**: Routing 和 Handoffs 创建逻辑约 90% 代码重复

**解决方案**:
- 提取公共方法 `createSubAgentTools(List<SubAgentConfig>, AgentConfig)`
- 封装创建 SubAgentTool 列表的公共逻辑
- Routing 和 Handoffs 方法调用该公共方法

**公共方法签名**:
```java
private List<SubAgentTool> createSubAgentTools(
    List<SubAgentConfig> subAgents,
    AgentConfig parentConfig
) { ... }
```

## 测试策略

| 问题 | 测试类型 | 测试类 | 验证点 |
|------|----------|--------|--------|
| P2-5 | 启动测试 | - | 应用正常启动，无类加载错误 |
| P2-4 | 单元测试 | JacksonConfigTest | ObjectMapper Bean 正常创建和使用 |
| P2-2 | 单元测试 | ChatControllerTest | uploadFile 响应不包含路径信息 |
| P2-1 | 集成测试 | PipelineAgentRuntimeTest | Pipeline 执行正常，无阻塞警告 |
| P2-3 | 单元测试 | CompositeAgentFactoryTest | ROUTING 和 HANDOFFS 类型 Agent 正常创建 |

## 执行顺序

采用批量修复方案，一次性完成所有修复：

1. 代码修改
   - 删除 CorrectSkillDiagnostic.java
   - 新增 JacksonConfig.java
   - 修改 ChatController.java (P2-2)
   - 修改 PipelineAgentRuntime.java (P2-1)
   - 修改 CompositeAgentFactory.java (P2-3)
   - 修改 AgentRuntime.java (P2-4)

2. 测试编写
   - 编写对应的单元测试和集成测试

3. 验证
   - 执行 `mvn clean test` 验证所有测试通过
   - 执行 `mvn clean compile` 验证编译通过

## 风险评估

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| ObjectMapper配置变化影响序列化 | 中 | 使用 @Primary 确保优先级，保持默认配置 |
| 移除.filePath字段破坏前端兼容性 | 低 | 前端仅使用 fileId，filePath 未实际使用 |
| .block()移除引入新的并发问题 | 中 | 使用 boundedElastic 调度器，确保隔离 |
| 提取公共方法引入新的bug | 低 | 充分的单元测试覆盖 |

## 退出标准

- [ ] 所有代码修改完成
- [ ] 所有新编写测试通过
- [ ] `mvn clean test` 执行成功
- [ ] `mvn clean compile` 执行成功
- [ ] 无新增警告信息
- [ ] 代码审查通过
