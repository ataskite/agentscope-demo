# Supervisor / Router + Shared Blackboard 设计文档

## 1. 当前问题（为什么需要这个架构）

仓库原有的 `ROUTING` / `HANDOFFS` 多 agent 实现存在三个核心问题：

1. **子 agent 不共享业务上下文**：`CompositeAgentFactory.createRoutingAgent` 把每个子 agent
   `.stateStore(new InMemoryAgentStateStore())` 单独构造，主 agent 和子 agent、子 agent
   互相之间没有任何共享业务事实的机制。用户跨专家切换（例如从「还款专家」转到「投诉专家」）
   时，新专家看不到上一专家已经收集到的金额、订单号、失败原因等业务事实，只能让用户重复提供。
2. **会话身份没有端到端传播**：`AgentRuntime` 最终调用的 `agent.streamEvents(msg)` 没有
   `RuntimeContext`，真实 `userId` / `sessionId` 在调用链末端丢失，导致会话状态寻址退回到
   `defaultSessionId(agentId)` 这种全局 fallback。
3. **`SubAgentProvider` 违反契约**：原代码 `() -> subAgent` 永远返回同一个预构建实例，
   违反了 AgentScope 2.0 GA `SubAgentProvider.provide()` 「每次返回新实例」的线程安全契约。

## 2. 三层状态边界

本架构显式区分三类状态，**绝不通过「让所有 agent 共用同一个 `agent_state`」实现共享上下文**
（这是明令禁止的反模式，原因见第 9 节）：

| 层 | 物理存储 key | 寻址维度 | 归属 | 内容 |
|----|----|----|----|----|
| **Conversation AgentState** | `"agent_state"` | `(userId, sessionId)` | Supervisor 独占 | 用户对话、摘要、Supervisor 工具调用、最终回复 |
| **Shared Blackboard** | `"shared_blackboard"` (可配) | `(userId, sessionId)` | 跨专家共享，**Supervisor 唯一写入** | activeExpert、currentIntent、customerFacts、collectedSlots、businessState、findings、unresolvedQuestions |
| **Expert Private State** | 各专家独立 `InMemoryAgentStateStore` | `(userId, sessionId::expertId)` | 单次 dispatch 生命周期 | 专家自己的 PermissionContext / ToolContext / TaskContext / PlanModeContext / 内部对话 |

注意三层虽然物理上都可以放在同一个 `AgentStateStore` 后端（InMemory / JsonFile / Redis / MySQL / PG），
但它们使用的 **key 完全不同**，且 AgentScope 的 SPI 按 `(userId, sessionId, key)` 三元组寻址，
绝不会混淆。

## 3. 数据流与时序

单轮 Supervisor 调用：

```
┌───────────────────────────────────────────────────────────────────┐
│ 1. ChatController.sendMessage                                      │
│    POST /chat/send  { agentId=customer-service-supervisor,         │
│                       sessionId=..., userId=..., message=... }     │
└──────────────┬─────────────────────────────────────────────────────┘
               │
               ▼
┌───────────────────────────────────────────────────────────────────┐
│ 2. AgentService.createStreamFlux                                   │
│    - 读 YAML config: type=ROUTING && sharedBlackboard.enabled=true │
│    - 委托 SupervisorRuntimeFactory（绕过 Legacy routing 路径）     │
└──────────────┬─────────────────────────────────────────────────────┘
               │
               ▼
┌───────────────────────────────────────────────────────────────────┐
│ 3. SupervisorRuntimeFactory.create                                 │
│    - 从 SessionManagerService 取 supervisorStore（会话级复用）     │
│    - 从 YAML 构建 Supervisor ReActAgent（绑 supervisorStore）      │
│    - 构造 ExpertAgentProvider（每次 provide 新实例）               │
└──────────────┬─────────────────────────────────────────────────────┘
               │
               ▼
┌───────────────────────────────────────────────────────────────────┐
│ 4. SupervisorRuntime.stream(msg)  ── 加 per-slot 锁 (串行)         │
│                                                                    │
│  a. snapshot = blackboard.getOrCreate(userId, sessionId)           │
│  b. supervisorState.appendUserMessage(msg)                         │
│  c. decision = RoutingDecisionService.decide(msg, summary, snap)   │
│     → KEEP / SWITCH / CLARIFY                                      │
│  d. emit routing_event {action, previousExpert, selectedExpert,    │
│                         reason, sessionId, blackboardVersion,      │
│                         confidence, timestamp}                     │
│  e. if CLARIFY → 写 Supervisor state + 回复澄清问题，结束          │
│  f. expert = ExpertAgentProvider.provide(expertId, user, session)  │
│     └─ 全新 InMemoryAgentStateStore + 全新 ReActAgent              │
│  g. expertCtx = RuntimeContext.builder()                           │
│         .userId(userId)                                            │
│         .sessionId(sessionId + "::" + expertId)                    │
│         .build()                                                   │
│  h. expertReply = expert.call([expertPrompt(snap, summary)], ctx)  │
│  i. result = parseExpertResult(expertReply)                        │
│     {expertId, answer, confidence, blackboardPatch, questions}     │
│  j. blackboard.applyPatch(userId, sessionId, coercedPatch)         │
│     └─ Supervisor 是唯一写入者，版本号 +1                           │
│  k. emit text 事件把 answer 推给前端                                │
│  l. supervisorState.appendAssistantMessage(answer)                 │
│  m. 解锁                                                            │
└───────────────────────────────────────────────────────────────────┘
```

**关键不变量**：
- 步骤 (a) 拿到的 `snapshot` 是 `BlackboardService` 返回的防御性副本，专家对它的任何修改都不会影响内部状态；
- 步骤 (f) 的专家 store 与 Supervisor store 是不同实例；
- 步骤 (g) 的 `sessionId::expertId` 让即使两个专家共享一个 store（实际不共享），寻址也不会撞；
- 步骤 (j) 的 patch 合并受 per-slot `ReentrantLock` 保护，**串行执行**，版本号单调。

## 4. Shared Blackboard Schema

```yaml
# SessionBlackboard —— 存储在 AgentStateStore key="shared_blackboard"
version: long              # 单调递增，每次 applyPatch +1
activeExpert: string       # 当前激活的专家 agentId（运行时状态，禁写入 YAML）
currentIntent: string      # 当前用户意图摘要
customerFacts: map         # 跨专家业务事实（如 settlementAmount、orderId）
collectedSlots: map        # 任务槽位（如 收货地址、发票抬头）
businessState: map         # 业务流程状态（如 complaintStatus: open）
findings: [ExpertFinding]  # 审计轨迹：谁加了什么 key=value，为什么
  - expertId: string
    key: string
    value: any
    rationale: string
    timestamp: long
unresolvedQuestions: [string]  # 待向用户澄清的问题
createdAt: long
updatedAt: long
```

**Patch 语义**（`BlackboardPatch`）：
- `activeExpert` / `currentIntent` 非空则替换
- `customerFactsPatch` / `collectedSlotsPatch` / `businessStatePatch`：浅合并；
  value 为 `null` 表示删除该 key
- `findingsToAdd`：追加到 findings
- `unresolvedQuestions`：非 null 则整体替换（`null` = 不变，`[]` = 清空）

## 5. 路由决策契约

`RoutingDecisionService` 是纯函数（无副作用），输入 `(supervisorConfig, userMessage, blackboardSnapshot)`，
输出 `RoutingDecision{action, selectedExpert, reason, blackboardVersion, confidence}`。

### 5.1 规则策略（`strategy: rule`，默认）

优先级（从高到低）：

1. **EXPLICIT triggers**：用户显式说「转 XX」 → `SWITCH`
2. **INTENT triggers（多命中时优先非当前专家）**：
   - 收集所有命中的 INTENT trigger
   - 若至少一个 target ≠ activeExpert → `SWITCH` 到第一个不同的
   - 否则 `KEEP` 当前专家
3. **无命中**：
   - activeExpert 非空且 `defaultKeep=true`（默认）→ `KEEP`
   - activeExpert 非空且 `defaultKeep=false` → `CLARIFY`
   - activeExpert 为空（首轮）→ 路由到 `subAgents` 第一个，避免把用户卡在 CLARIFY

### 5.2 LLM 策略（`strategy: llm`，预留）

`SupervisorRuntime` 可在调用本服务前先调 Supervisor LLM 取得高置信路由；
本服务的 `decideLlm` 分支为 fallback，保证「未配置 LLM」也能正常工作。

### 5.3 MULTI（未实现）

`RoutingAction.MULTI` 已在枚举中声明，但当前版本不使用——目标明确把垂直切片限定在
KEEP/SWITCH/CLARIFY。

## 6. 并发与一致性边界

| 维度 | 保证 | 当前限制 |
|----|----|----|
| 同一 `(userId, sessionId)` 的路由+专家调用+patch | **进程内串行**（per-slot `ReentrantLock`） | 跨进程无锁 |
| 版本号 | 单调递增，每次 patch +1 | — |
| 不同 `(userId, sessionId)` 之间 | 完全隔离，可并行 | — |
| 分布式后端（Redis/MySQL/PG profile） | 复用 S13 已有的 AgentStateStore bean | **无跨进程 CAS**，多实例并发写同一 slot 仍可能 last-write-wins |
| 防御性复制 | `BlackboardService` 所有读路径返回 deep copy | — |

**演进方向**：当需要真正的多实例并发写时，可在 `BlackboardService.applyPatch` 中引入
基于版本号的乐观锁（Redis: Lua 脚本；MySQL/PG: `UPDATE ... WHERE version=?`），
但目前不假装已经实现。

## 7. RuntimeContext 端到端传播

`ChatRequest.userId / sessionId` → `AgentService.createStreamFlux` →
`SupervisorRuntimeFactory.create` → `SupervisorRuntime` →
`RuntimeContext.builder().userId(userId).sessionId(expertScopedSessionId).build()`
→ `agent.call(msgs, runtimeContext)`（AgentScope 2.0 GA 真实 API）。

不再依赖 `defaultSessionId(agentId)` 作为 fallback；`defaultSessionId` 仍保留在
ReActAgent builder 里，只是因为它在 AgentScope SPI 里是必填字段，但运行时永远
通过 `RuntimeContext` 显式覆盖。

## 8. 本次实现范围

**新增**：
- `src/main/java/com/skloda/agentscope/blackboard/`（8 个类）
  - `SessionBlackboard` / `BlackboardPatch` / `ExpertFinding`：数据模型
  - `ExpertRequest` / `ExpertResult`：专家调用契约
  - `RoutingAction` / `RoutingDecisionService`：路由决策
  - `ExpertAgentProvider`：专家工厂
  - `BlackboardService`：黑板服务（串行 + 防御复制 + 版本号）
  - `SupervisorRuntime` / `SupervisorRuntimeFactory`：Supervisor 运行时
- `AgentConfig` 新增 `routingConfig` + `sharedBlackboard` 两个嵌套配置类（默认关闭）
- `AgentService` 新增 Supervisor 分支，传播 userId/sessionId
- `agents.yml` 新增 `customer-service-supervisor` 示例 agent
- `src/test/java/com/skloda/agentscope/blackboard/`（8 个测试类，42 个 case）

**保持不变**：
- 所有 Legacy ROUTING / HANDOFFS 配置（未启用 `sharedBlackboard` 时走原路径）
- 所有现有 multi-agent pattern（SEQUENTIAL / PARALLEL / DEBATE / LOOP / MSG_HUB 等）
- `CompositeAgentFactory.createRoutingAgent` / `createHandoffsAgent` 原代码不动
- 前端无任何改动（固定发 `customer-service-supervisor` agentId 即可）

## 9. 为什么不用「所有 agent 共用一个 `agent_state`」？

目标显式禁止的方案：

```java
// 禁止：让专家直接共享 Supervisor 的 stateStore
ReActAgent subAgent = ReActAgent.builder()
    .stateStore(effectiveStore)   // ← 这会让 Router 和专家互相覆盖 AgentState
    ...
```

**后果**：
1. Router 调用专家是嵌套执行，专家在执行过程中会把自己推理/工具调用写进同一个 context list，
   Router 拿回控制权后看到的 context 已经被污染；
2. 专家的 `PermissionContext` / `ToolContext` / `TaskContext` / `PlanModeContext`
   会覆盖 Supervisor 的对应字段（last-write-wins）；
3. 两个专家先后执行时，第一个专家的内部状态会污染第二个专家看到的 context；
4. 这不是真正的「共享业务事实」——它只是把所有东西塞进同一个无结构的 list，
   专家根本分不清哪些是业务事实、哪些是另一个专家的工具调用。

**本架构的正确做法**：
- 业务事实显式提取到独立的 `SessionBlackboard`（结构化字段，不与对话历史混存）
- 专家读到的是 `snapshot`（防御性副本），写的不是 blackboard 而是 `BlackboardPatch`，
  由 Supervisor 校验合并；
- 专家的 AgentState 物理隔离在独立 store + 独立 session id 下。

## 10. 非目标

- 不重写其它 6 种 multi-agent pattern（SEQUENTIAL / PARALLEL / DEBATE / LOOP / MSG_HUB / SUBAGENT_*）
- 不修改 HANDOFFS agent 的运行时行为（即使 YAML 误配 `sharedBlackboard.enabled=true`，
  AgentService 也会因为 `type != ROUTING` 而 fallthrough 到 legacy 路径）
- 不实现 MULTI 单轮多专家串行
- 不实现 LLM 路由（预留 hook，由调用方在 SupervisorRuntime 层补充）
- 不实现跨进程原子 patch（一致性边界如第 6 节所述）

## 11. 后续演进

- **跨进程原子 patch**：在 `BlackboardService.applyPatch` 加 version-based optimistic lock
  （Redis: Lua；MySQL/PG: `UPDATE WHERE version=?`）
- **LLM 路由策略**：在 `SupervisorRuntime` 调用本服务前先走 Supervisor LLM，输出结构化
  `{action, expertId, confidence}`，置信度低于 `minConfidence` 时回落到规则策略
- **MULTI 模式**：单轮内按需调用多个专家，每个专家 patch 顺序合并
- **Blackboard TTL / 清理**：基于 `updatedAt` 的定期清理任务
- **专家私有 state 持久化**：如果需要专家跨 dispatch 保持私有上下文，
  可在 `ExpertAgentProvider` 中改用 `(userId, sessionId::expertId)` 寻址的持久化 store
  （但默认仍是短生命周期实例，避免污染 Supervisor）
