package com.skloda.agentscope.middleware;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.ModelCallInput;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P3-4 / P3-5 / P2-7: rate limiting concurrency, and PII log masking.
 */
class RateLimitAndMaskingRegressionTest {

    private final RuntimeContext ctx = RuntimeContext.empty();

    private ModelCallInput input() {
        return new ModelCallInput(List.of(), List.of(), null, null);
    }

    @Test
    void concurrentCallsStayWithinLimitAndQueueStaysSane() throws Exception {
        RateLimitMiddleware middleware = new RateLimitMiddleware(50);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        AtomicInteger errors = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(200);

        for (int i = 0; i < 200; i++) {
            pool.submit(() -> {
                try {
                    // P3-5: over-limit calls now return Flux.error instead of Flux.empty()
                    middleware.onModelCall(null, ctx, input(), in -> Flux.<AgentEvent>empty())
                            .subscribe(v -> {}, e -> errors.incrementAndGet());
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        assertTrue(latch.await(15, TimeUnit.SECONDS), "Concurrent calls deadlocked");
        pool.shutdownNow();

        // 200 calls against a limit of 50: the majority must be rejected loudly, and
        // the concurrency (previously a corrupt LinkedList) must not wedge the middleware
        assertTrue(errors.get() >= 100,
                "Expected most over-limit calls to be rejected, got errors=" + errors.get());
    }

    // ---- P2-7: PII masking ----

    @Test
    void maskChineseIdCard() {
        String masked = LogMasking.mask("客户张三身份证号110101199001011234请核对");
        assertFalse(masked.contains("110101199001011234"));
        assertTrue(masked.contains("1101**********34"));
    }

    @Test
    void maskPhoneNumber() {
        String masked = LogMasking.mask("联系电话13800138000备用");
        assertFalse(masked.contains("13800138000"));
        assertTrue(masked.contains("138*****00"));
    }

    @Test
    void maskEmail() {
        String masked = LogMasking.mask("邮箱zhangsan@example.com已登记");
        assertFalse(masked.contains("zhangsan@example.com"));
        assertTrue(masked.contains("z***@example.com"));
    }

    @Test
    void plainTextSurvivesUnchanged() {
        assertEquals("普通文本不含敏感信息", LogMasking.mask("普通文本不含敏感信息"));
        assertEquals("", LogMasking.mask(""));
        assertNull(LogMasking.mask(null));
    }
}
