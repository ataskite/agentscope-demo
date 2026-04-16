package com.skloda.agentscope.service;

import com.skloda.agentscope.runtime.AgentRuntime;
import com.skloda.agentscope.runtime.AgentRuntimeFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * Agent service using AgentRuntime for clean session management.
 * Each request gets a fresh runtime with proper resource cleanup.
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final AgentRuntimeFactory runtimeFactory;

    public AgentService(AgentRuntimeFactory runtimeFactory) {
        this.runtimeFactory = runtimeFactory;
    }

    /**
     * Create stream Flux for agent interaction.
     * Runtime cleanup is handled by AgentRuntime.stream() internally.
     */
    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName) {
        AgentRuntime runtime = runtimeFactory.createRuntime(agentId);

        // Prepend file attachment info if provided
        String actualMessage = preprocessMessage(filePath, fileName, message);

        // Build user message
        Msg userMsg = Msg.builder()
                .role(MsgRole.USER)
                .content(TextBlock.builder().text(actualMessage).build())
                .build();

        // Stream from runtime (cleanup handled internally)
        return runtime.stream(userMsg);
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
}
