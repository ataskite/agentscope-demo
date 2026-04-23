package com.skloda.agentscope.service;

import com.skloda.agentscope.model.ChatRequest;
import com.skloda.agentscope.model.MultiModalMessage;
import com.skloda.agentscope.runtime.AgentRuntime;
import com.skloda.agentscope.runtime.AgentRuntimeFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent service supporting both stateless and session-aware streaming.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRuntimeFactory runtimeFactory;
    private final SessionManagerService sessionManagerService;

    public AgentService(AgentRuntimeFactory runtimeFactory,
                        SessionManagerService sessionManagerService) {
        this.runtimeFactory = runtimeFactory;
        this.sessionManagerService = sessionManagerService;
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

        // Session mode: reuse cached agent
        if (sessionId != null && !sessionId.isBlank()) {
            return createSessionStreamFlux(sessionId, agentId, userMsg);
        }

        // Stateless mode: fresh agent per request
        AgentRuntime runtime = runtimeFactory.createRuntime(agentId);
        return runtime.stream(userMsg);
    }

    private Flux<Map<String, Object>> createSessionStreamFlux(String sessionId, String agentId,
                                                                Msg userMsg) {
        SessionManagerService.SessionContext ctx =
                sessionManagerService.getOrCreateSession(sessionId, agentId);

        // The actual sessionId might be new (if input was null)
        String effectiveSessionId = ctx.getSessionId();

        // Create runtime with shared memory — new agent per request, memory persists
        AgentRuntime runtime = runtimeFactory.createRuntimeWithMemory(agentId, ctx.getMemory());

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
