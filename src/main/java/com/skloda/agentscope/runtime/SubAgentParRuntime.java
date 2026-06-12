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
 * Parallel sub-agent runtime — dispatches tasks concurrently, aggregates results.
 */
public class SubAgentParRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(SubAgentParRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<SubAgentTask> tasks;
    private final String pipelineId;

    public SubAgentParRuntime(List<SubAgentTask> tasks, ObservabilityHook hook, String pipelineId) {
        this.tasks = tasks;
        this.hook = hook;
        this.pipelineId = pipelineId;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> pipelineFlux = Flux.create(fluxSink -> {
            String input = extractText(userMsg);

            List<Mono<Map<String, Object>>> taskMonos = new ArrayList<>();
            for (SubAgentTask task : tasks) {
                hook.emitTaskDelegate("dispatcher", task.agentId, task.taskDescription);
                hook.emitTaskStart(task.agentId);

                String taskContent = task.taskDescription.replace("{input}", input);
                Msg taskMsg = Msg.builder()
                        .name("user").role(MsgRole.USER)
                        .textContent(taskContent).build();

                Mono<Map<String, Object>> taskMono = task.agent.call(taskMsg).map(response -> {
                    String output = extractText(response);
                    hook.emitTaskEnd(task.agentId, truncate(output, 200));
                    return Map.<String, Object>of("agentId", task.agentId, "output", output);
                });
                taskMonos.add(taskMono);
            }

            Flux.merge(taskMonos)
                    .collectList()
                    .subscribe(
                            results -> {
                                hook.emitTaskAggregate(tasks.size());
                                StringBuilder combined = new StringBuilder();
                                for (Map<String, Object> r : results) {
                                    combined.append("## ").append(r.get("agentId")).append("\n");
                                    combined.append(r.get("output")).append("\n\n");
                                    fluxSink.next(Map.of("type", "task_result",
                                            "agentId", r.get("agentId"),
                                            "output", truncate((String) r.get("output"), 500)));
                                }
                                fluxSink.next(Map.of("type", "text", "content", combined.toString()));
                                fluxSink.next(Map.of("type", "done"));
                                fluxSink.complete();
                            },
                            error -> {
                                log.error("SubAgent parallel pipeline error", error);
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

    public record SubAgentTask(ReActAgent agent, String agentId, String taskDescription) {}
}
