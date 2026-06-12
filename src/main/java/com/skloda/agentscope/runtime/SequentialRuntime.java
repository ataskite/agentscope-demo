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

/**
 * Sequential pipeline runtime — executes agents in order, chaining outputs.
 */
public class SequentialRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(SequentialRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<ReActAgent> agents;
    private final String pipelineId;

    public SequentialRuntime(List<ReActAgent> agents, ObservabilityHook hook, String pipelineId) {
        this.agents = agents;
        this.hook = hook;
        this.pipelineId = pipelineId;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> pipelineFlux = Flux.create(fluxSink -> {
            hook.emitPipelineStart(pipelineId, agents.stream().map(ReActAgent::getName).toList());

            Mono<String> chain = Mono.just(extractText(userMsg));

            for (int i = 0; i < agents.size(); i++) {
                final int stepIndex = i;
                final ReActAgent agent = agents.get(i);

                chain = chain.flatMap(prevOutput -> {
                    hook.emitPipelineStepStart(pipelineId, stepIndex, agent.getName());
                    long start = System.currentTimeMillis();

                    Msg stepMsg = Msg.builder()
                            .name("user").role(MsgRole.USER)
                            .textContent(prevOutput).build();

                    return agent.call(stepMsg).map(response -> {
                        String output = extractText(response);
                        long duration = System.currentTimeMillis() - start;
                        hook.emitPipelineStepEnd(pipelineId, stepIndex, agent.getName(), duration);
                        fluxSink.next(Map.of("type", "pipeline_step_result",
                                "stepIndex", stepIndex, "agentId", agent.getName(),
                                "output", truncate(output, 500)));
                        return output;
                    });
                });
            }

            chain.subscribe(
                    finalOutput -> {
                        fluxSink.next(Map.of("type", "text", "content", finalOutput));
                        hook.emitPipelineEnd(pipelineId, agents.size(), 0);
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

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) sb.append(tb.getText());
        }
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }

    @Override
    public void close() {
        hook.getEventSink().complete();
    }
}
