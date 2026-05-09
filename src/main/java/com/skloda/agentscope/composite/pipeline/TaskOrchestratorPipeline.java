package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;

public class TaskOrchestratorPipeline implements Pipeline<Msg> {

    private final List<AgentBase> agents;
    private final List<String> taskTemplates;
    private final List<String> agentIds;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public TaskOrchestratorPipeline(List<AgentBase> agents, List<String> taskTemplates, List<String> agentIds) {
        this.agents = agents;
        this.taskTemplates = taskTemplates;
        this.agentIds = agentIds;
    }

    public void addEventConsumer(BiConsumer<String, Map<String, Object>> consumer) {
        eventConsumers.add(consumer);
    }

    @Override
    public Mono<Msg> execute(Msg input) {
        return execute(input, null);
    }

    @Override
    public Mono<Msg> execute(Msg input, Class<?> structuredOutputClass) {
        String inputText = extractText(input);
        return runStep(0, inputText, null);
    }

    private Mono<Msg> runStep(int index, String originalInput, String prevOutput) {
        if (index >= agents.size()) {
            return Mono.just(buildTextMsg(prevOutput != null ? prevOutput : "No output"));
        }

        AgentBase agent = agents.get(index);
        String agentId = index < agentIds.size() ? agentIds.get(index) : ("agent-" + index);
        String template = index < taskTemplates.size() ? taskTemplates.get(index) : "{input}";

        String task = template;
        if (prevOutput != null) {
            task = task.replace("{prevOutput}", prevOutput);
        }
        task = task.replace("{input}", originalInput);

        emit("task_delegate", Map.of(
                "from", "orchestrator",
                "to", agentId,
                "task", truncate(task, 200)
        ));
        emit("task_start", Map.of("agent", agentId));

        Msg agentMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(task).build())
                .build();

        return agent.call(agentMsg)
                .flatMap(output -> {
                    String outputText = extractText(output);
                    emit("task_end", Map.of(
                            "agent", agentId,
                            "outputPreview", truncate(outputText, 200)
                    ));
                    return runStep(index + 1, originalInput, outputText);
                });
    }

    private String extractText(Msg msg) {
        if (msg == null || msg.getContent() == null) return "";
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : msg.getContent()) {
            if (block instanceof TextBlock tb) {
                sb.append(tb.getText());
            }
        }
        return sb.toString();
    }

    private Msg buildTextMsg(String text) {
        return Msg.builder()
                .role(MsgRole.ASSISTANT)
                .content(TextBlock.builder().text(text).build())
                .build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void emit(String type, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("type", type);
        for (var consumer : eventConsumers) {
            consumer.accept(type, payload);
        }
    }
}
