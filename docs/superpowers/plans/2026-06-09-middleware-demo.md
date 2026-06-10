# Middleware Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a dedicated middleware-demo agent showcasing AgentScope 2.0's Middleware system with three middlewares: audit logging, rate limiting, and context enrichment.

**Architecture:** Three `MiddlewareBase` implementations registered via a `MiddlewareRegistry` (name→supplier map, modeled on `ToolRegistry`). `AgentConfig` gets a `middlewares` list field. `AgentFactory.buildAgent()` resolves middleware names and registers them with `ReActAgent.Builder.middlewares()`.

**Tech Stack:** Java 17, Spring Boot 3.5, AgentScope 2.0.0-RC1 (`io.agentscope.core.middleware.*`), Project Reactor

---

### Task 1: Add `middlewares` field to AgentConfig

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentConfig.java:74`

- [ ] **Step 1: Add middlewares field**

Add after the existing `samplePrompts` field (line 77):

```java
    // === Middleware fields ===
    private List<String> middlewares = new ArrayList<>();
```

- [ ] **Step 2: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentConfig.java
git commit -m "feat: add middlewares field to AgentConfig"
```

---

### Task 2: Create MiddlewareRegistry

**Files:**
- Create: `src/main/java/com/skloda/agentscope/middleware/MiddlewareRegistry.java`
- Test: `src/test/java/com/skloda/agentscope/middleware/MiddlewareRegistryTest.java`

- [ ] **Step 1: Write the test**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.middleware.MiddlewareBase;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class MiddlewareRegistryTest {

    @Test
    void registerAndCreate() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        registry.register("test-mw", () -> new StubMiddleware());
        MiddlewareBase mw = registry.create("test-mw");
        assertNotNull(mw);
        assertTrue(mw instanceof StubMiddleware);
    }

    @Test
    void createUnknownReturnsNull() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        assertNull(registry.create("nonexistent"));
    }

    @Test
    void listNames() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        registry.register("a", StubMiddleware::new);
        registry.register("b", StubMiddleware::new);
        assertEquals(2, registry.getRegisteredNames().size());
        assertTrue(registry.getRegisteredNames().contains("a"));
    }

    static class StubMiddleware extends MiddlewareBase {}
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=MiddlewareRegistryTest -pl . 2>&1 | tail -5`
Expected: FAIL — class not found

- [ ] **Step 3: Implement MiddlewareRegistry**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Supplier;

@Component
public class MiddlewareRegistry {

    private static final Logger log = LoggerFactory.getLogger(MiddlewareRegistry.class);
    private final Map<String, Supplier<MiddlewareBase>> registry = new LinkedHashMap<>();

    public void register(String name, Supplier<MiddlewareBase> factory) {
        registry.put(name, factory);
        log.debug("Registered middleware: {}", name);
    }

    public MiddlewareBase create(String name) {
        Supplier<MiddlewareBase> factory = registry.get(name);
        if (factory == null) {
            log.warn("Middleware not found: {}", name);
            return null;
        }
        return factory.get();
    }

    public List<String> getRegisteredNames() {
        return List.copyOf(registry.keySet());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=MiddlewareRegistryTest -pl . 2>&1 | tail -5`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/middleware/MiddlewareRegistry.java src/test/java/com/skloda/agentscope/middleware/MiddlewareRegistryTest.java
git commit -m "feat: add MiddlewareRegistry with name-to-instance mapping"
```

---

### Task 3: Create AuditLoggingMiddleware

**Files:**
- Create: `src/main/java/com/skloda/agentscope/middleware/AuditLoggingMiddleware.java`
- Test: `src/test/java/com/skloda/agentscope/middleware/AuditLoggingMiddlewareTest.java`

- [ ] **Step 1: Write the test**

The test verifies that the middleware logs correctly by capturing SLF4J output and that it correctly delegates to `next`.

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolUseBlock;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggingMiddlewareTest {

    private final AuditLoggingMiddleware mw = new AuditLoggingMiddleware();

    @Test
    void onAgentDelegatesToNext() {
        List<Msg> msgs = List.of(
                Msg.builder().content(TextBlock.builder().text("hello").build()).build()
        );
        AgentInput input = new AgentInput(msgs);
        TextBlockDeltaEvent fakeEvent = new TextBlockDeltaEvent("r1", "b1", "hi");

        Flux<AgentEvent> result = mw.onAgent(
                null, input,
                in -> Flux.just(fakeEvent)
        );

        List<AgentEvent> events = result.collectList().block();
        assertEquals(1, events.size());
        assertEquals("hi", ((TextBlockDeltaEvent) events.get(0)).getDelta());
    }

    @Test
    void onReasoningDelegatesToNext() {
        ReasoningInput input = new ReasoningInput(List.of(), List.of(), null);
        TextBlockDeltaEvent fakeEvent = new TextBlockDeltaEvent("r1", "b1", "thinking");

        Flux<AgentEvent> result = mw.onReasoning(
                null, input,
                in -> Flux.just(fakeEvent)
        );

        List<AgentEvent> events = result.collectList().block();
        assertEquals(1, events.size());
    }

    @Test
    void onActingDelegatesToNext() {
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .id("t1").name("web_search").input(Map.of("query", "test")).build();
        ActingInput input = new ActingInput(List.of(toolUse));
        TextBlockDeltaEvent fakeEvent = new TextBlockDeltaEvent("r1", "b1", "result");

        Flux<AgentEvent> result = mw.onActing(
                null, input,
                in -> Flux.just(fakeEvent)
        );

        List<AgentEvent> events = result.collectList().block();
        assertEquals(1, events.size());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AuditLoggingMiddlewareTest 2>&1 | tail -5`
Expected: FAIL

- [ ] **Step 3: Implement AuditLoggingMiddleware**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.*;
import io.agentscope.core.message.ToolUseBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.List;

public class AuditLoggingMiddleware extends MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(AuditLoggingMiddleware.class);

    @Override
    public Flux<AgentEvent> onAgent(Agent agent, AgentInput input,
                                     Function<AgentInput, Flux<AgentEvent>> next) {
        String agentName = agent != null ? agent.getName() : "unknown";
        int msgCount = input.msgs() != null ? input.msgs().size() : 0;
        log.info("[audit] Agent '{}' starting with {} input messages", agentName, msgCount);
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] Agent '{}' completed in {}ms", agentName, durationMs);
                })
                .doOnError(e -> log.error("[audit] Agent '{}' failed: {}", agentName, e.getMessage()));
    }

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, ReasoningInput input,
                                         Function<ReasoningInput, Flux<AgentEvent>> next) {
        int msgCount = input.messages() != null ? input.messages().size() : 0;
        log.info("[audit] Reasoning phase starting with {} messages", msgCount);
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] Reasoning phase completed in {}ms", durationMs);
                });
    }

    @Override
    public Flux<AgentEvent> onActing(Agent agent, ActingInput input,
                                      Function<ActingInput, Flux<AgentEvent>> next) {
        List<ToolUseBlock> toolCalls = input.toolCalls();
        for (ToolUseBlock tool : toolCalls) {
            String params = tool.getInput() != null ? tool.getInput().toString() : "{}";
            String preview = params.length() > 80 ? params.substring(0, 80) + "..." : params;
            log.info("[audit] Tool '{}' called with params: {}", tool.getName(), preview);
        }
        long startNanos = System.nanoTime();

        return next.apply(input)
                .doOnComplete(() -> {
                    long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                    log.info("[audit] Tool execution completed in {}ms", durationMs);
                });
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AuditLoggingMiddlewareTest 2>&1 | tail -5`
Expected: Tests run: 3, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/middleware/AuditLoggingMiddleware.java src/test/java/com/skloda/agentscope/middleware/AuditLoggingMiddlewareTest.java
git commit -m "feat: add AuditLoggingMiddleware with agent/reasoning/acting hooks"
```

---

### Task 4: Create RateLimitMiddleware

**Files:**
- Create: `src/main/java/com/skloda/agentscope/middleware/RateLimitMiddleware.java`
- Test: `src/test/java/com/skloda/agentscope/middleware/RateLimitMiddlewareTest.java`

- [ ] **Step 1: Write the test**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ModelCallInput;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitMiddlewareTest {

    private final RateLimitMiddleware mw = new RateLimitMiddleware(3);

    @Test
    void allowsUpToLimit() {
        for (int i = 0; i < 3; i++) {
            Flux<AgentEvent> result = mw.onModelCall(null,
                    new ModelCallInput(List.of(), List.of(), null, null),
                    in -> Flux.empty());
            StepVerifier.create(result).verifyComplete();
        }
    }

    @Test
    void blocksWhenLimitExceeded() {
        // Exhaust the limit
        for (int i = 0; i < 3; i++) {
            mw.onModelCall(null,
                    new ModelCallInput(List.of(), List.of(), null, null),
                    in -> Flux.empty()).blockLast();
        }

        // Next call should be blocked (next not invoked)
        boolean[] nextCalled = {false};
        Flux<AgentEvent> result = mw.onModelCall(null,
                new ModelCallInput(List.of(), List.of(), null, null),
                in -> {
                    nextCalled[0] = true;
                    return Flux.empty();
                });

        StepVerifier.create(result).verifyComplete();
        assertFalse(nextCalled[0], "next should not be called when rate limited");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=RateLimitMiddlewareTest 2>&1 | tail -5`
Expected: FAIL

- [ ] **Step 3: Implement RateLimitMiddleware**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ModelCallInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Function;

public class RateLimitMiddleware extends MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(RateLimitMiddleware.class);

    private final int maxCallsPerMinute;
    private final Queue<Long> callTimestamps = new LinkedList<>();

    public RateLimitMiddleware() {
        this(10);
    }

    public RateLimitMiddleware(int maxCallsPerMinute) {
        this.maxCallsPerMinute = maxCallsPerMinute;
    }

    @Override
    public Flux<AgentEvent> onModelCall(Agent agent, ModelCallInput input,
                                         Function<ModelCallInput, Flux<AgentEvent>> next) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000;

        // Remove timestamps outside the 1-minute window
        while (!callTimestamps.isEmpty() && callTimestamps.peek() < windowStart) {
            callTimestamps.poll();
        }

        if (callTimestamps.size() >= maxCallsPerMinute) {
            log.warn("[rate-limit] Model call blocked — {}/{} calls in last minute",
                    callTimestamps.size(), maxCallsPerMinute);
            return Flux.empty();
        }

        callTimestamps.add(now);
        log.debug("[rate-limit] Model call allowed — {}/{} in window",
                callTimestamps.size(), maxCallsPerMinute);
        return next.apply(input);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=RateLimitMiddlewareTest 2>&1 | tail -5`
Expected: Tests run: 2, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/middleware/RateLimitMiddleware.java src/test/java/com/skloda/agentscope/middleware/RateLimitMiddlewareTest.java
git commit -m "feat: add RateLimitMiddleware with sliding window rate limiting"
```

---

### Task 5: Create ContextEnrichmentMiddleware

**Files:**
- Create: `src/main/java/com/skloda/agentscope/middleware/ContextEnrichmentMiddleware.java`
- Test: `src/test/java/com/skloda/agentscope/middleware/ContextEnrichmentMiddlewareTest.java`

- [ ] **Step 1: Write the test**

```java
package com.skloda.agentscope.middleware;

import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

class ContextEnrichmentMiddlewareTest {

    private final ContextEnrichmentMiddleware mw = new ContextEnrichmentMiddleware();

    @Test
    void appendsContextSection() {
        String original = "You are a helpful assistant.";
        String result = mw.onSystemPrompt(null, original).block();

        assertNotNull(result);
        assertTrue(result.startsWith(original), "Should preserve original prompt");
        assertTrue(result.contains("<context>"), "Should add <context> section");
        assertTrue(result.contains("</context>"), "Should close <context> section");
        assertTrue(result.contains("当前时间"), "Should include current time label");
    }

    @Test
    void doesNotModifyOriginal() {
        String original = "Original prompt";
        String result = mw.onSystemPrompt(null, original).block();
        assertNotEquals(original, result, "Should return a new string");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContextEnrichmentMiddlewareTest 2>&1 | tail -5`
Expected: FAIL

- [ ] **Step 3: Implement ContextEnrichmentMiddleware**

```java
package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.middleware.MiddlewareBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ContextEnrichmentMiddleware extends MiddlewareBase {

    private static final Logger log = LoggerFactory.getLogger(ContextEnrichmentMiddleware.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Mono<String> onSystemPrompt(Agent agent, String currentPrompt) {
        String now = LocalDateTime.now().format(FMT);
        String context = String.format(
                "%s%n%n<context>%n当前时间: %s%n用户身份: demo-user%n</context>",
                currentPrompt, now
        );
        log.debug("[context-enrichment] Injected timestamp: {}", now);
        return Mono.just(context);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ContextEnrichmentMiddlewareTest 2>&1 | tail -5`
Expected: Tests run: 2, Failures: 0

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/middleware/ContextEnrichmentMiddleware.java src/test/java/com/skloda/agentscope/middleware/ContextEnrichmentMiddlewareTest.java
git commit -m "feat: add ContextEnrichmentMiddleware for dynamic prompt injection"
```

---

### Task 6: Auto-register middlewares in MiddlewareRegistry

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/middleware/MiddlewareRegistry.java`
- Modify: `src/test/java/com/skloda/agentscope/middleware/MiddlewareRegistryTest.java`

- [ ] **Step 1: Add auto-registration test to existing test**

Add to `MiddlewareRegistryTest`:

```java
    @Test
    void autoRegistrationCreatesAllMiddlewares() {
        MiddlewareRegistry registry = new MiddlewareRegistry();
        registry.register("audit-logging", AuditLoggingMiddleware::new);
        registry.register("rate-limit", RateLimitMiddleware::new);
        registry.register("context-enrichment", ContextEnrichmentMiddleware::new);

        assertNotNull(registry.create("audit-logging"));
        assertNotNull(registry.create("rate-limit"));
        assertNotNull(registry.create("context-enrichment"));
        assertEquals(3, registry.getRegisteredNames().size());
    }
```

- [ ] **Step 2: Run test to verify it passes**

Run: `mvn test -Dtest=MiddlewareRegistryTest 2>&1 | tail -5`
Expected: Tests run: 4, Failures: 0

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/skloda/agentscope/middleware/MiddlewareRegistryTest.java
git commit -m "test: add auto-registration test for middleware registry"
```

---

### Task 7: Integrate MiddlewareRegistry into AgentFactory

**Files:**
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java:46-53` (constructor)
- Modify: `src/main/java/com/skloda/agentscope/agent/AgentFactory.java:146-151` (buildAgent, before return)

- [ ] **Step 1: Add MiddlewareRegistry dependency to AgentFactory constructor**

Add import and field:

```java
import com.skloda.agentscope.middleware.MiddlewareRegistry;
import io.agentscope.core.middleware.MiddlewareBase;
```

Add field after `mcpClientService`:

```java
    private final MiddlewareRegistry middlewareRegistry;
```

Update constructor — add `MiddlewareRegistry` parameter:

```java
    public AgentFactory(AgentConfigService configService, ToolRegistry toolRegistry,
                        KnowledgeService knowledgeService, McpClientService mcpClientService,
                        MiddlewareRegistry middlewareRegistry) {
        this.configService = configService;
        this.toolRegistry = toolRegistry;
        this.knowledgeService = knowledgeService;
        this.mcpClientService = mcpClientService;
        this.middlewareRegistry = middlewareRegistry;
    }
```

- [ ] **Step 2: Add middleware registration to buildAgent(), before `return builder.build()`**

Insert after the hooks registration block (after line 146) and before `builder.enablePendingToolRecovery(true)`:

```java
        // Register middlewares if configured
        if (config.getMiddlewares() != null && !config.getMiddlewares().isEmpty()) {
            List<MiddlewareBase> middlewares = config.getMiddlewares().stream()
                    .map(middlewareRegistry::create)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            if (!middlewares.isEmpty()) {
                builder.middlewares(middlewares);
                log.info("  Registered {} middlewares for agent: {}", middlewares.size(), agentId);
            }
        }
```

- [ ] **Step 3: Verify compilation**

Run: `mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run full test suite**

Run: `mvn test 2>&1 | grep -E "Tests run:|BUILD"`
Expected: All existing tests pass. Total tests should be ~310+ (existing + new middleware tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/skloda/agentscope/agent/AgentFactory.java
git commit -m "feat: integrate MiddlewareRegistry into AgentFactory"
```

---

### Task 8: Add middleware-demo agent to agents.yml

**Files:**
- Modify: `src/main/resources/config/agents.yml`

- [ ] **Step 1: Add middleware-demo agent config**

Append at the end of the agents list (before the last line if there's an explicit end marker, or as the last entry):

```yaml

  # === 2.0 Middleware Demo ===

  - agentId: middleware-demo
    category: demo
    type: SINGLE
    name: 中间件演示助手
    description: 展示 AgentScope 2.0 中间件系统：审计日志、限流、上下文注入
    modelName: qwen-plus
    streaming: true
    enableThinking: true
    middlewares:
      - audit-logging
      - rate-limit
      - context-enrichment
    systemPrompt: |
      你是一个中间件演示助手。你的每次调用都会经过审计日志、限流检查和上下文注入中间件。
      请正常回答用户的问题，展示中间件的工作效果。
    samplePrompts:
      - prompt: "你好，请问现在几点了？"
        expectedBehavior: "ContextEnrichmentMiddleware 注入当前时间，AuditLoggingMiddleware 记录调用日志"
      - prompt: "帮我搜索今天的天气"
        expectedBehavior: "工具调用经过审计中间件记录，限流中间件检查"
```

- [ ] **Step 2: Verify the app loads the config**

Run: `mvn test -Dtest=AgentScopeDemoApplicationTest 2>&1 | grep "Loaded agent config.*middleware-demo"`
Expected: Log line showing middleware-demo loaded

- [ ] **Step 3: Run full test suite**

Run: `mvn test 2>&1 | grep -E "Tests run:|BUILD"`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/config/agents.yml
git commit -m "feat: add middleware-demo agent to agents.yml"
```

---

### Task 9: End-to-end verification

**Files:** No new files

- [ ] **Step 1: Run full test suite**

Run: `mvn test 2>&1 | tail -10`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Verify compilation with package**

Run: `mvn clean package -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Final commit (if any remaining uncommitted changes)**

Only if there are uncommitted changes:
```bash
git status
# If clean, no commit needed
```
