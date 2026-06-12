package com.skloda.agentscope.runtime;

import com.skloda.agentscope.composite.graph.OrderFulfillmentGraph;
import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public class StateGraphRuntime implements StreamingAgentRuntime {

    private final String graphName;
    private final OrderFulfillmentGraph graph;
    private final ObservabilityHook hook;

    public StateGraphRuntime(String graphName, OrderFulfillmentGraph graph, ObservabilityHook hook) {
        this.graphName = graphName;
        this.graph = graph;
        this.hook = hook;

        graph.addEventConsumer((type, data) -> {
            hook.getEventSink().emit(type, data);
        });
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> graphStream = Flux.<Map<String, Object>>create(fluxSink -> {
            try {
                hook.emitPipelineStart(graphName, List.of());
                long startNanos = System.nanoTime();

                Msg result = graph.execute(userMsg).block();
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

                if (result != null && result.getContent() != null) {
                    for (ContentBlock block : result.getContent()) {
                        if (block instanceof TextBlock tb) {
                            String text = tb.getText();
                            if (text != null && !text.isEmpty()) {
                                fluxSink.next(Map.of("type", "text", "content", text));
                            }
                        }
                    }
                }

                hook.emitPipelineEnd(graphName, 1, durationMs);
                fluxSink.next(Map.of("type", "done"));
                close();
                fluxSink.complete();
            } catch (Exception e) {
                fluxSink.next(Map.of("type", "error", "message", e.getMessage()));
                close();
                fluxSink.complete();
            }
        });

        return Flux.merge(hook.getEventSink().asFlux(), graphStream)
                .doOnCancel(this::close);
    }

    @Override
    public ObservabilityHook getHook() {
        return hook;
    }

    @Override
    public void close() {
        hook.getEventSink().complete();
    }
}
