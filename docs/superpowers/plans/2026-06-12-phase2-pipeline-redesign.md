# AgentScope 2.0 Phase 2: Pipeline 模式重设计 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 用 AgentScope 2.0 的 SubAgentTool + agent.call() + EventSink 重新实现 7 种 Pipeline 多 Agent 模式，恢复全部 agent 类型功能。

**Architecture:** 每种模式创建独立的 PipelineRuntime 类，实现 `StreamingAgentRuntime` 接口。使用 `agent.call()` 进行同步调用编排，`EventSink` 发射多 Agent SSE 事件。不再依赖已删除的 `Pipeline<Msg>` 接口。

**Tech Stack:** AgentScope 2.0.0-RC3, Spring Boot 3.5, Java 17, Project Reactor

---

## File Structure

**Create:**
- `src/main/java/com/skloda/agentscope/runtime/SequentialRuntime.java` — 顺序执行 runtime
- `src/main/java/com/skloda/agentscope/runtime/ParallelRuntime.java` — 并发执行 runtime
- `src/main/java/com/skloda/agentscope/runtime/DebateRuntime.java` — 辩论 runtime
- `src/main/java/com/skloda/agentscope/runtime/LoopRuntime.java` — 迭代优化 runtime
- `src/main/java/com/skloda/agentscope/runtime/MsgHubRuntime.java` — 专家圆桌 runtime (已有，需更新)
- `src/main/java/com/skloda/agentscope/runtime/SubAgentSeqRuntime.java` — 子 Agent 顺序编排 runtime
- `src/main/java/com/skloda/agentscope/runtime/SubAgentParRuntime.java` — 子 Agent 并行分发 runtime

**Modify:**
- `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java` — 移除 UnsupportedOperationException，接入新 Runtime
- `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java` — 添加新模式工厂方法
- `src/main/resources/config/agents.yml` — 添加 7 个新模式 agent 配置

---

## Task 1: 创建 SequentialRuntime

**Create:** `src/main/java/com/skloda/agentscope/runtime/SequentialRuntime.java`

顺序执行：按序调用各子 agent，上一步输出注入下一步输入。

```java
package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public class SequentialRuntime implements StreamingAgentRuntime {
    private static final Logger log = LoggerFactory.getLogger(SequentialRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<ReActAgent> agents;
    private final String pipelineId;
    private final EventSink sink;

    public SequentialRuntime(List<ReActAgent> agents, ObservabilityHook hook, String pipelineId) {
        this.agents = agents;
        this.hook = hook;
        this.pipelineId = pipelineId;
        this.sink = hook.getEventSink();
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = sink.asFlux();

        Flux<Map<String, Object>> pipelineFlux = Flux.create(fluxSink -> {
            sink.emitPipelineStart(pipelineId, agents.stream().map(ReActAgent::getName).toList());

            Mono<String> chain = Mono.just(extractText(userMsg));

            for (int i = 0; i < agents.size(); i++) {
                final int stepIndex = i;
                final ReActAgent agent = agents.get(i);

                chain = chain.flatMap(prevOutput -> {
                    sink.emitPipelineStepStart(pipelineId, stepIndex, agent.getName());
                    long start = System.currentTimeMillis();

                    Msg stepMsg = buildMsg(agent.getName(), prevOutput);
                    return agent.call(stepMsg)
                            .map(response -> {
                                String output = extractText(response);
                                long duration = System.currentTimeMillis() - start;
                                sink.emitPipelineStepEnd(pipelineId, stepIndex, agent.getName(), duration);
                                fluxSink.next(Map.of("type", "pipeline_step_result",
                                        "stepIndex", stepIndex, "agentId", agent.getName(),
                                        "output", output));
                                return output;
                            });
                });
            }

            chain.subscribe(
                    finalOutput -> {
                        sink.emitPipelineEnd(pipelineId, agents.size(), 0);
                        fluxSink.next(Map.of("type", "done"));
                        fluxSink.complete();
                    },
                    error -> {
                        log.error("Sequential pipeline error", error);
                        fluxSink.next(Map.of("type", "error", "message", error.getMessage()));
                        fluxSink.next(Map.of("type", "done"));
                        fluxSink.complete();
                    }
            );
        });

        return Flux.merge(sinkEvents, pipelineFlux).doOnCancel(this::close);
    }

    private Msg buildMsg(String agentName, String content) {
        return Msg.builder().name("user").role(MsgRole.USER).textContent(content).build();
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) sb.append(tb.getText());
        }
        return sb.toString();
    }

    @Override
    public void close() {
        sink.complete();
    }
}
```

- [ ] Create the file
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add SequentialRuntime for sequential pipeline pattern`

---

## Task 2: 创建 ParallelRuntime

**Create:** `src/main/java/com/skloda/agentscope/runtime/ParallelRuntime.java`

并发执行：所有子 agent 同时接收相同消息，结果聚合。

Same structure as SequentialRuntime but uses `Flux.merge()` for parallel execution.

- [ ] Create the file using `Flux.merge(agentMonos).collectList()` pattern
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add ParallelRuntime for parallel pipeline pattern`

---

## Task 3: 创建 DebateRuntime

**Create:** `src/main/java/com/skloda/agentscope/runtime/DebateRuntime.java`

辩论模式：多个专家依次发言，judge 最后综合评判。支持多轮。

- [ ] Create the file with multi-round debate + judge synthesis
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add DebateRuntime for debate pattern`

---

## Task 4: 创建 LoopRuntime

**Create:** `src/main/java/com/skloda/agentscope/runtime/LoopRuntime.java`

迭代优化：writer 生成 → critic 评审 → 检查退出条件（质量阈值或最大迭代次数）。

- [ ] Create the file with writer→critic loop and exit condition
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add LoopRuntime for iterative refinement pattern`

---

## Task 5: 更新 MsgHubRuntime

**Modify:** `src/main/java/com/skloda/agentscope/runtime/MsgHubRuntime.java`

专家圆桌：多轮讨论，每轮每位专家发言，最后 moderator 总结。

- [ ] Read current file and update to use new EventSink API
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: update MsgHubRuntime for 2.0 EventSink API`

---

## Task 6: 创建 SubAgentSeqRuntime 和 SubAgentParRuntime

**Create:**
- `src/main/java/com/skloda/agentscope/runtime/SubAgentSeqRuntime.java`
- `src/main/java/com/skloda/agentscope/runtime/SubAgentParRuntime.java`

SubAgentSeq: 链式委托，`{prevOutput}` 模板变量。
SubAgentPar: 并发分发，结果收集聚合。

- [ ] Create both files
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add SubAgentSeqRuntime and SubAgentParRuntime`

---

## Task 7: 更新 AgentRuntimeFactory 和 CompositeAgentFactory

**Modify:**
- `src/main/java/com/skloda/agentscope/runtime/AgentRuntimeFactory.java`
- `src/main/java/com/skloda/agentscope/composite/CompositeAgentFactory.java`

- [ ] Replace `throw UnsupportedOperationException` with actual runtime creation
- [ ] Add factory methods for new patterns in CompositeAgentFactory
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: wire pipeline runtimes into AgentRuntimeFactory`

---

## Task 8: 添加 agents.yml 配置

**Modify:** `src/main/resources/config/agents.yml`

为 7 种模式添加 agent 配置，复用现有 expert agents。

- [ ] Add 7 agent configs
- [ ] `mvn clean compile -q` verify
- [ ] Commit: `feat: add pipeline pattern agent configurations`

---

## Task 9: 最终验证

- [ ] `mvn clean compile` — BUILD SUCCESS
- [ ] Verify no UnsupportedOperationException for any agent type
- [ ] Commit: `chore: Phase 2 complete — Pipeline patterns redesigned for AgentScope 2.0`
