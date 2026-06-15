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
 * Sequential sub-agent runtime — chains agent outputs via {prevOutput} template.
 * Each agent receives the previous agent's output via template variables.
 *
 * <p>Each sub-agent runs via {@code streamEvents()} (bridged by {@link MultiAgentStreamSupport}),
 * so the frontend receives every thinking/tool/text delta from every step.
 */
public class SubAgentSeqRuntime implements StreamingAgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(SubAgentSeqRuntime.class);

    @Getter
    private final ObservabilityHook hook;
    private final List<SubAgentStep> steps;
    private final String pipelineId;

    public SubAgentSeqRuntime(List<SubAgentStep> steps, ObservabilityHook hook, String pipelineId) {
        this.steps = steps;
        this.hook = hook;
        this.pipelineId = pipelineId;
    }

    @Override
    public Flux<Map<String, Object>> stream(Msg userMsg) {
        Flux<Map<String, Object>> sinkEvents = hook.getEventSink().asFlux();

        Flux<Map<String, Object>> pipelineFlux = Flux.create(fluxSink -> {
            String originalInput = MultiAgentStreamSupport.extractText(userMsg);

            Mono<String> chain = Mono.just(originalInput);

            for (int i = 0; i < steps.size(); i++) {
                final int stepIndex = i;
                final SubAgentStep step = steps.get(i);

                chain = chain.flatMap(prevOutput -> {
                    hook.emitTaskDelegate(
                            stepIndex > 0 ? steps.get(stepIndex - 1).agentId : "user",
                            step.agentId, step.taskTemplate);
                    hook.emitTaskStart(step.agentId);

                    String task = step.taskTemplate
                            .replace("{input}", originalInput)
                            .replace("{prevOutput}", prevOutput);

                    Msg taskMsg = Msg.builder()
                            .name("user").role(MsgRole.USER)
                            .textContent(task).build();

                    return MultiAgentStreamSupport.runSubAgent(step.agent, taskMsg, step.agentId, fluxSink, null)
                            .map(output -> {
                                hook.emitTaskEnd(step.agentId, truncate(output, 200));
                                fluxSink.next(Map.of("type", "task_result",
                                        "stepIndex", stepIndex, "agentId", step.agentId,
                                        "output", truncate(output, 500)));
                                return output;
                            });
                });
            }

            chain.subscribe(
                    finalOutput -> {
                        hook.emitTaskAggregate(steps.size());
                        fluxSink.next(Map.of("type", "text", "content", finalOutput));
                        fluxSink.next(Map.of("type", "done"));
                        fluxSink.complete();
                    },
                    error -> {
                        log.error("SubAgent sequential pipeline error", error);
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

    public record SubAgentStep(ReActAgent agent, String agentId, String taskTemplate) {}
}
