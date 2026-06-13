package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.RuntimeContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextEnrichmentMiddlewareTest {

    private final ContextEnrichmentMiddleware mw = new ContextEnrichmentMiddleware();
    private final RuntimeContext ctx = RuntimeContext.empty();

    @Test
    void appendsContextSection() {
        String original = "You are a helpful assistant.";
        String result = mw.onSystemPrompt(null, ctx, original).block();

        assertNotNull(result);
        assertTrue(result.startsWith(original), "Should preserve original prompt");
        assertTrue(result.contains("<context>"), "Should add <context> section");
        assertTrue(result.contains("</context>"), "Should close <context> section");
        assertTrue(result.contains("当前时间"), "Should include current time label");
    }

    @Test
    void doesNotModifyOriginal() {
        String original = "Original prompt";
        String result = mw.onSystemPrompt(null, ctx, original).block();
        assertNotEquals(original, result, "Should return a new string");
    }
}
