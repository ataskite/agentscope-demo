package com.skloda.agentscope.composite.pipeline;

import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.pipeline.Pipeline;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.function.BiConsumer;

public class TaskDispatcherPipeline implements Pipeline<Msg> {

    private final List<AgentBase> agents;
    private final List<String> taskTemplates;
    private final List<String> agentIds;
    private final List<BiConsumer<String, Map<String, Object>>> eventConsumers = new ArrayList<>();

    public TaskDispatcherPipeline(List<AgentBase> agents, List<String> taskTemplates, List<String> agentIds) {
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

        List<Mono<Msg>> agentMonos = new ArrayList<>();
        for (int i = 0; i < agents.size(); i++) {
            AgentBase agent = agents.get(i);
            String agentId = i < agentIds.size() ? agentIds.get(i) : ("agent-" + i);
            String template = i < taskTemplates.size() ? taskTemplates.get(i) : "{input}";
            String task = template.replace("{input}", inputText);

            emit("task_delegate", Map.of(
                    "from", "dispatcher",
                    "to", agentId,
                    "task", truncate(task, 200)
            ));
            emit("task_start", Map.of("agent", agentId));

            Msg agentMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(task).build())
                    .build();

            agentMonos.add(agent.call(agentMsg).map(output -> {
                String outputText = extractText(output);
                emit("task_end", Map.of(
                        "agent", agentId,
                        "outputPreview", truncate(outputText, 200)
                ));
                return output;
            }));
        }

        return Flux.merge(agentMonos)
                .collectList()
                .map(results -> {
                    emit("task_aggregate", Map.of("totalTasks", results.size()));

                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < results.size(); i++) {
                        String agentId = i < agentIds.size() ? agentIds.get(i) : ("agent-" + i);
                        sb.append("## ").append(agentId).append(" 的结果\n\n");
                        String text = extractText(results.get(i));
                        sb.append(text).append("\n\n");
                    }
                    return Msg.builder()
                            .role(MsgRole.ASSISTANT)
                            .content(TextBlock.builder().text(sb.toString()).build())
                            .build();
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
