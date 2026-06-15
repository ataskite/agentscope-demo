package com.skloda.agentscope.runtime;

import com.skloda.agentscope.hook.ObservabilityHook;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.*;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parallel pipeline runtime — executes all agents concurrently, aggregates results.
 *
 * <p>Each sub-agent runs via {@code streamEvents()} (bridged by {@link MultiAgentStreamSupport}),
 * so the frontend receives every thinking/tool/text delta from every agent. Concurrent agents
 * share one {@code fluxSink} (thread-safe); each event is tagged with its agent's source label.
 */
public class ParallelRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(ParallelRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<ReActAgent> agents;
    private final String pipelineId;

    public ParallelRuntime(List<ReActAgent> agents, ObservabilityHook hook, String pipelineId) {
        this.agents = agents;
        this.hook = hook;
        this.pipelineId = pipelineId;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> pipelineFlux = Flux.create(fluxSink -> {
            List<String> agentNames = agents.stream().map(ReActAgent::getName).toList();
            hook.emitPipelineStart(pipelineId, agentNames);
            long pipelineStart = System.currentTimeMillis();

            List<Mono<Map<String, Object>>> agentMonos = new ArrayList<>();
            for (int i = 0; i < agents.size(); i++) {
                final int stepIndex = i;
                final ReActAgent agent = agents.get(i);

                hook.emitPipelineStepStart(pipelineId, stepIndex, agent.getName());
                long start = System.currentTimeMillis();

                Mono<Map<String, Object>> agentMono = MultiAgentStreamSupport
                        .runSubAgent(agent, userMsg, agent.getName(), fluxSink, null)
                        .map(output -> {
                            long duration = System.currentTimeMillis() - start;
                            hook.emitPipelineStepEnd(pipelineId, stepIndex, agent.getName(), duration);
                            return Map.<String, Object>of("agentId", agent.getName(), "output", output);
                        });
                agentMonos.add(agentMono);
            }

            Flux.merge(agentMonos)
                    .collectList()
                    .subscribe(
                            results -> {
                                StringBuilder combined = new StringBuilder();
                                for (Map<String, Object> r : results) {
                                    combined.append("### ").append(r.get("agentId")).append("\n");
                                    combined.append(r.get("output")).append("\n\n");
                                    fluxSink.next(Map.of("type", "pipeline_step_result",
                                            "agentId", r.get("agentId"),
                                            "output", truncate((String) r.get("output"), 500)));
                                }

                                fluxSink.next(Map.of("type", "text", "content", combined.toString()));
                                long totalDuration = System.currentTimeMillis() - pipelineStart;
                                hook.emitPipelineEnd(pipelineId, agents.size(), totalDuration);
                                fluxSink.next(Map.of("type", "done"));
                                fluxSink.complete();
                            },
                            error -> {
                                log.error("Parallel pipeline error", error);
                                fluxSink.next(Map.of("type", "error", "message", error.getMessage()));
                                fluxSink.next(Map.of("type", "done"));
                                fluxSink.complete();
                            }
                    );
        });

        return Flux.merge(sinkEvents, pipelineFlux.doFinally(s -> close()))
                .doOnCancel(this::close);
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
