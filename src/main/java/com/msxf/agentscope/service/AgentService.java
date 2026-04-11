package com.msxf.agentscope.service;

import com.msxf.agentscope.model.SimpleTools;
import com.msxf.agentscope.tool.DocxParserTool;
import com.msxf.agentscope.tool.PdfParserTool;
import com.msxf.agentscope.tool.XlsxParserTool;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.agent.Event;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.message.*;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.skill.repository.ClasspathSkillRepository;
import io.agentscope.core.tool.Toolkit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${agentscope.model.dashscope.api-key:}")
    private String apiKey;

    private final ConcurrentHashMap<String, ReActAgent> agents = new ConcurrentHashMap<>();

    public ReActAgent getAgent(String agentType) {
        return agents.computeIfAbsent(agentType, this::createAgent);
    }

    private ReActAgent createAgent(String agentType) {
        DashScopeChatModel model = DashScopeChatModel.builder()
                .apiKey(apiKey)
                .modelName("qwen-plus")
                .stream(true)
                .enableThinking(true)
                .formatter(new DashScopeChatFormatter())
                .build();

        ReActAgent.Builder builder = ReActAgent.builder()
                .name(switch (agentType) {
                    case "tool" -> "ToolAgent";
                    case "task" -> "TaskAgent";
                    case "template" -> "TemplateEditor";
                    default -> "Assistant";
                })
                .sysPrompt(switch (agentType) {
                    case "tool" -> "You are a helpful AI assistant with access to various tools. " +
                            "Use the appropriate tools when needed to answer questions accurately. " +
                            "Always explain what you're doing when using tools.";
                    case "task" -> "You are a document analysis assistant. " +
                            "When the user uploads a document, use the appropriate skill to parse it " +
                            "and then fulfill the user's request based on the extracted content. " +
                            "Support .docx, .pdf, and .xlsx file analysis.";
                    case "template" -> "You are a Word document template editor. " +
                            "When users upload a .docx template, help them fill in variables. " +
                            "First parse the document to identify placeholders, " +
                            "then ask for values if needed, then replace them using edit_docx.";
                    default -> "You are a helpful AI assistant. Be friendly and concise.";
                })
                .model(model)
                .memory(new InMemoryMemory());

        Toolkit toolkit = new Toolkit();

        switch (agentType) {
            case "tool" -> {
                toolkit.registerTool(new SimpleTools());
                builder.toolkit(toolkit);
            }
            case "task" -> {
                SkillBox skillBox = new SkillBox(toolkit);
                try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                    skillBox.registration()
                            .skill(repo.getSkill("docx"))
                            .tool(new DocxParserTool())
                            .apply();
                    skillBox.registration()
                            .skill(repo.getSkill("pdf"))
                            .tool(new PdfParserTool())
                            .apply();
                    skillBox.registration()
                            .skill(repo.getSkill("xlsx"))
                            .tool(new XlsxParserTool())
                            .apply();
                } catch (Exception e) {
                    log.error("Failed to load skills from classpath", e);
                }
                builder.toolkit(toolkit).skillBox(skillBox);
            }
            case "template" -> {
                SkillBox skillBox = new SkillBox(toolkit);
                try (ClasspathSkillRepository repo = new ClasspathSkillRepository("skills")) {
                    skillBox.registration()
                            .skill(repo.getSkill("docx-template"))
                            .tool(new DocxParserTool())
                            .apply();
                } catch (Exception e) {
                    log.error("Failed to load skills from classpath", e);
                }
                builder.toolkit(toolkit).skillBox(skillBox);
            }
            default -> builder.toolkit(toolkit);
        }

        return builder.build();
    }

    public void streamToEmitter(String agentType, String message, String filePath, String fileName, SseEmitter emitter) {
        ReActAgent agent = getAgent(agentType);

        String actualMessage = message;
        if (filePath != null && !filePath.isBlank()) {
            String fileInfo = String.format("[用户上传了文件: %s, 路径: %s]\n\n", fileName, filePath);
            actualMessage = fileInfo + message;
        }

        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
                .build();

        StreamOptions streamOptions = StreamOptions.builder()
                .eventTypes(EventType.REASONING, EventType.TOOL_RESULT)
                .incremental(true)
                .includeReasoningResult(false)
                .build();

        agent.stream(userMsg, streamOptions)
                .subscribe(
                        event -> {
                            try {
                                handleEvent(event, emitter);
                            } catch (Exception e) {
                                log.error("Error handling stream event", e);
                            }
                        },
                        error -> {
                            log.error("Stream error", error);
                            try {
                                sendEvent(emitter, "error", Map.of("message", error.getMessage() != null ? error.getMessage() : "Unknown error"));
                            } catch (Exception e) {
                                log.error("Error sending error event", e);
                            }
                            emitter.complete();
                        },
                        () -> {
                            try {
                                sendEvent(emitter, "done", Map.of());
                            } catch (Exception e) {
                                log.error("Error sending done event", e);
                            }
                            emitter.complete();
                        }
                );
    }

    private void handleEvent(Event event, SseEmitter emitter) throws Exception {
        Msg msg = event.getMessage();
        if (msg == null || msg.getContent() == null) {
            return;
        }

        for (ContentBlock block : msg.getContent()) {
            switch (block) {
                case ThinkingBlock tb -> {
                    String thinking = tb.getThinking();
                    if (thinking != null && !thinking.isEmpty()) {
                        sendEvent(emitter, "thinking", Map.of("content", thinking));
                    }
                }
                case TextBlock tb -> {
                    String text = tb.getText();
                    if (text != null && !text.isEmpty()) {
                        sendEvent(emitter, "text", Map.of("content", text));
                    }
                }
                case ToolUseBlock tub -> {
                    sendEvent(emitter, "tool_call", Map.of(
                            "name", tub.getName() != null ? tub.getName() : "",
                            "params", tub.getInput() != null ? tub.getInput().toString() : "{}"
                    ));
                }
                case ToolResultBlock trb -> {
                    String resultText = trb.getOutput() != null
                            ? trb.getOutput().stream()
                            .filter(o -> o instanceof TextBlock)
                            .map(o -> ((TextBlock) o).getText())
                            .reduce("", (a, b) -> a + b)
                            : "";
                    sendEvent(emitter, "tool_result", Map.of(
                            "name", trb.getName() != null ? trb.getName() : "",
                            "result", resultText
                    ));
                }
                default -> {}
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String type, Map<String, Object> data) throws Exception {
        Map<String, Object> payload = new java.util.LinkedHashMap<>(data);
        payload.put("type", type);
        String json = objectMapper.writeValueAsString(payload);
        emitter.send(SseEmitter.event().name("message").data(json));
    }
}
