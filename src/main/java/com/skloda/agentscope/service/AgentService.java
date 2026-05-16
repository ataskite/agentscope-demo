package com.skloda.agentscope.service;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentType;
import com.skloda.agentscope.harness.HarnessAgentService;
import com.skloda.agentscope.model.ChatRequest;
import com.skloda.agentscope.model.ChatMessage;
import com.skloda.agentscope.model.MultiModalMessage;
import com.skloda.agentscope.runtime.AgentRuntimeFactory;
import com.skloda.agentscope.runtime.StreamingAgentRuntime;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Agent service supporting both stateless and session-aware streaming.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRuntimeFactory runtimeFactory;
    private final SessionManagerService sessionManagerService;
    private final WorkflowRunService workflowRunService;
    private final ChatHistoryRepository chatHistoryRepository;
    private final HarnessAgentService harnessAgentService;

    @Autowired
    public AgentService(AgentRuntimeFactory runtimeFactory,
                        SessionManagerService sessionManagerService,
                        WorkflowRunService workflowRunService,
                        ChatHistoryRepository chatHistoryRepository,
                        HarnessAgentService harnessAgentService) {
        this.runtimeFactory = runtimeFactory;
        this.sessionManagerService = sessionManagerService;
        this.workflowRunService = workflowRunService;
        this.chatHistoryRepository = chatHistoryRepository;
        this.harnessAgentService = harnessAgentService;
    }

    public AgentService(AgentRuntimeFactory runtimeFactory,
                        SessionManagerService sessionManagerService,
                        WorkflowRunService workflowRunService,
                        ChatHistoryRepository chatHistoryRepository) {
        this(runtimeFactory, sessionManagerService, workflowRunService, chatHistoryRepository, null);
    }

    public AgentService(AgentRuntimeFactory runtimeFactory,
                        SessionManagerService sessionManagerService,
                        WorkflowRunService workflowRunService) {
        this(runtimeFactory, sessionManagerService, workflowRunService, new InMemoryChatHistoryRepository(), null);
    }

    public AgentService(AgentRuntimeFactory runtimeFactory,
                        SessionManagerService sessionManagerService) {
        this(runtimeFactory, sessionManagerService, new WorkflowRunService());
    }

    /**
     * Create stream Flux for agent interaction.
     * If sessionId is provided, uses cached agent from session; otherwise stateless.
     */
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId) {
        return createStreamFlux(agentId, message, filePath, fileName, sessionId, null, null);
    }

    /**
     * Create stream Flux with multi-modal support (images/audio).
     */
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       List<ChatRequest.ImageFile> images,
                                                       ChatRequest.AudioFile audio) {
        Msg userMsg = buildUserMessage(message, filePath, fileName, images, audio);

        // Route HARNESS type to HarnessAgentService
        if (harnessAgentService != null) {
            AgentConfig cfg = runtimeFactory.getConfigService().findAgentConfig(agentId).orElse(null);
            if (cfg != null && cfg.getType() == AgentType.HARNESS) {
                return harnessAgentService.createStreamFlux(agentId, message, filePath, fileName, sessionId);
            }
        }

        String runId = workflowRunService.startRun(agentId, sessionId,
                buildInputPreview(message, filePath, fileName, images, audio));

        // Session mode: reuse cached agent
        Flux<Map<String, Object>> stream;
        if (sessionId != null && !sessionId.isBlank()) {
            stream = createSessionStreamFlux(sessionId, agentId, userMsg);
        } else {
            // Stateless mode: fresh agent per request
            StreamingAgentRuntime runtime = runtimeFactory.createRuntime(agentId);
            stream = runtime.stream(userMsg);
        }

        return recordWorkflowRun(runId, recordChatTranscript(agentId,
                buildTranscriptUserText(message, fileName, images, audio), stream));
    }

    private Flux<Map<String, Object>> createSessionStreamFlux(String sessionId, String agentId,
                                                                Msg userMsg) {
        SessionManagerService.SessionContext ctx =
                sessionManagerService.getOrCreateSession(sessionId, agentId);

        // The actual sessionId might be new (if input was null)
        String effectiveSessionId = ctx.getSessionId();

        // Create runtime with shared memory — new agent per request, memory persists
        StreamingAgentRuntime runtime = runtimeFactory.createRuntimeWithMemory(agentId, ctx.getMemory());

        return runtime.stream(userMsg)
                .doFinally(signal -> {
                    // Save session after each interaction
                    sessionManagerService.saveSession(effectiveSessionId);
                    log.debug("Session {} saved after stream completion ({})", effectiveSessionId, signal);
                });
    }

    /**
     * Prepend file attachment info to message if file provided.
     */
    private String preprocessMessage(String filePath, String fileName, String message) {
        if (filePath != null && !filePath.isBlank()) {
            return String.format("[用户上传了文件: %s, 路径: %s]\n\n%s", fileName, filePath, message);
        }
        return message;
    }

    private Flux<Map<String, Object>> recordWorkflowRun(String runId, Flux<Map<String, Object>> stream) {
        AtomicBoolean terminalRecorded = new AtomicBoolean(false);
        return stream
                .doOnNext(event -> {
                    workflowRunService.recordEvent(runId, event);
                    if ("done".equals(event.get("type")) && terminalRecorded.compareAndSet(false, true)) {
                        workflowRunService.completeRun(runId);
                    }
                })
                .doOnError(error -> {
                    if (terminalRecorded.compareAndSet(false, true)) {
                        workflowRunService.failRun(runId, error);
                    }
                });
    }

    private Flux<Map<String, Object>> recordChatTranscript(String agentId, String userText,
                                                           Flux<Map<String, Object>> stream) {
        return Flux.defer(() -> {
            chatHistoryRepository.append(agentId, ChatMessage.user(userText != null ? userText : ""));
            StringBuilder assistantText = new StringBuilder();
            StringBuilder thinkingText = new StringBuilder();
            AtomicBoolean assistantRecorded = new AtomicBoolean(false);
            return stream
                    .doOnNext(event -> {
                        String type = String.valueOf(event.get("type"));
                        if ("text".equals(type)) {
                            Object content = event.getOrDefault("content", event.get("text"));
                            if (content != null) {
                                assistantText.append(content);
                            }
                        }
                        if ("thinking".equals(type) || "reasoning_text".equals(type)) {
                            Object content = event.getOrDefault("content", event.get("text"));
                            if (content != null) {
                                thinkingText.append(content);
                            }
                        }
                        if ("done".equals(type)
                                && assistantText.length() > 0
                                && assistantRecorded.compareAndSet(false, true)) {
                            chatHistoryRepository.append(agentId,
                                    ChatMessage.assistant(assistantText.toString(),
                                            thinkingText.length() > 0 ? thinkingText.toString() : null));
                        }
                    });
        });
    }

    private String buildInputPreview(String message, String filePath, String fileName,
                                     List<ChatRequest.ImageFile> images,
                                     ChatRequest.AudioFile audio) {
        if (audio != null && audio.getPath() != null) {
            return "[audio: " + audio.getFileName() + "]";
        }
        String text = preprocessMessage(filePath, fileName, message);
        if (images != null && !images.isEmpty()) {
            return text + "\n[images: " + images.size() + "]";
        }
        return text;
    }

    private String buildTranscriptUserText(String message, String fileName,
                                           List<ChatRequest.ImageFile> images,
                                           ChatRequest.AudioFile audio) {
        StringBuilder text = new StringBuilder(message != null ? message : "");
        if (fileName != null && !fileName.isBlank()) {
            appendTranscriptAttachment(text, "文件: " + fileName);
        }
        if (images != null && !images.isEmpty()) {
            appendTranscriptAttachment(text, "图片: " + images.size() + " 张");
        }
        if (audio != null && audio.getFileName() != null && !audio.getFileName().isBlank()) {
            appendTranscriptAttachment(text, "音频: " + audio.getFileName());
        }
        return text.toString();
    }

    private void appendTranscriptAttachment(StringBuilder text, String attachment) {
        if (!text.isEmpty()) {
            text.append("\n");
        }
        text.append("[").append(attachment).append("]");
    }

    /**
     * Build user message with support for text, images, and audio.
     */
    private Msg buildUserMessage(String message, String filePath, String fileName,
                                 List<ChatRequest.ImageFile> images,
                                 ChatRequest.AudioFile audio) {
        // Audio-only message (for voice assistant)
        if (audio != null && audio.getPath() != null) {
            return MultiModalMessage.withAudio(audio.getPath(), audio.getFileName());
        }

        // Image(s) + text message
        if (images != null && !images.isEmpty()) {
            String text = preprocessMessage(filePath, fileName, message);
            if (images.size() == 1) {
                ChatRequest.ImageFile img = images.get(0);
                return MultiModalMessage.withImage(text, img.getPath(), img.getFileName());
            } else {
                List<MultiModalMessage.ImageFile> imgList = images.stream()
                        .map(img -> new MultiModalMessage.ImageFile(img.getPath(), img.getFileName()))
                        .toList();
                return MultiModalMessage.withMultipleImages(text, imgList);
            }
        }

        // Text-only message
        String actualMessage = preprocessMessage(filePath, fileName, message);
        return Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
                .build();
    }
}
