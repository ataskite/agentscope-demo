package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

class PipelineAgentRuntimeTest {

    private ObservabilityHook hook;

    @BeforeEach
    void setUp() {
        hook = new ObservabilityHook();
    }

    private Pipeline<Msg> simplePipeline(String text) {
        return new Pipeline<>() {
            @Override
            public Mono<Msg> execute(Msg msg) {
                return Mono.just(Msg.builder().content(TextBlock.builder().text(text).build()).build());
            }
            @Override
            public Mono<Msg> execute(Msg msg, Class<?> cls) { return execute(msg); }
        };
    }

    @Test
    void closeRemovesHookConsumer() {
        PipelineAgentRuntime runtime = new PipelineAgentRuntime("close-test", simplePipeline("x"), hook);
        runtime.close();
        assertEquals("close-test", runtime.getPipelineName());
    }

    @Test
    void getHookReturnsSameHook() {
        PipelineAgentRuntime runtime = new PipelineAgentRuntime("hook-test", simplePipeline("x"), hook);
        assertSame(hook, runtime.getHook());
        runtime.close();
    }

    @Test
    void constructorSetsFields() {
        PipelineAgentRuntime runtime = new PipelineAgentRuntime("my-pipe", simplePipeline("a"), hook);
        assertEquals("my-pipe", runtime.getPipelineName());
        assertNotNull(runtime.getHook());
        runtime.close();
    }
}
