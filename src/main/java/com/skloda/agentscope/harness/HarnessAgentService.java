package com.skloda.agentscope.harness;

import com.skloda.agentscope.agent.AgentConfig;
import com.skloda.agentscope.agent.AgentConfigService;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.harness.agent.HarnessAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class HarnessAgentService {

    private static final Logger log = LoggerFactory.getLogger(HarnessAgentService.class);

    private final AgentConfigService configService;
    private final HarnessAgentFactory harnessAgentFactory;
    private final String apiKey;
    private final ConcurrentHashMap<String, HarnessAgent> agentCache = new ConcurrentHashMap<>();

    public HarnessAgentService(AgentConfigService configService,
                               HarnessAgentFactory harnessAgentFactory,
                               @Value("${agentscope.model.dashscope.api-key:}") String apiKey) {
        this.configService = configService;
        this.harnessAgentFactory = harnessAgentFactory;
        this.apiKey = apiKey;
    }

    // P2-3: whitelist — executionMode arrives verbatim from the request body and was
    // previously used as a cache key, so every random value built and cached a full
    // HarnessAgent (client-driven memory amplification).
    private static final java.util.Set<String> ALLOWED_EXECUTION_MODES = java.util.Set.of("CLAW", "BUILDER");

    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId,
                                                       String userId,
                                                       String executionMode) {
        try {
            String normalizedMode = normalizeExecutionMode(executionMode);
            if (normalizedMode == null) {
                return Flux.just(Map.of("type", "error",
                        "message", "Invalid executionMode: '" + executionMode + "'. Allowed values: CLAW, BUILDER"));
            }
            HarnessAgent agent = getOrCreateAgent(agentId, normalizedMode);

            String actualMessage = prependFileInfo(filePath, fileName, message);
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(actualMessage).build())
                    .build();

            String effectiveUserId = (userId != null && !userId.isBlank()) ? userId : "demo-user";
            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId != null ? sessionId : "default")
                    .userId(effectiveUserId)
                    .build();

            HarnessRuntime runtime = new HarnessRuntime(agent, ctx);
            return runtime.stream(userMsg);
        } catch (Exception e) {
            log.error("Failed to create harness stream for agent: {}", agentId, e);
            return Flux.just(Map.of("type", "error", "message", e.getMessage()));
        }
    }

    private String normalizeExecutionMode(String executionMode) {
        String normalized = (executionMode != null && !executionMode.isBlank())
                ? executionMode.trim().toUpperCase() : "CLAW";
        return ALLOWED_EXECUTION_MODES.contains(normalized) ? normalized : null;
    }

    private HarnessAgent getOrCreateAgent(String agentId, String executionMode) throws Exception {
        String cacheKey = agentId + ":" + executionMode;
        return agentCache.computeIfAbsent(cacheKey, key -> {
            try {
                AgentConfig config = configService.getAgentConfig(agentId);
                String effectiveKey = resolveApiKey();
                return harnessAgentFactory.create(config, effectiveKey, executionMode);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create HarnessAgent: " + key, e);
            }
        });
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String envKey = System.getenv("DASHSCOPE_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        throw new IllegalStateException("No DashScope API key configured. Set agentscope.model.dashscope.api-key or DASHSCOPE_API_KEY env var.");
    }

    private String prependFileInfo(String filePath, String fileName, String message) {
        if (filePath != null && !filePath.isBlank()) {
            // P1-2: same upload-whitelist rule as AgentService.preprocessMessage
            if (!com.skloda.agentscope.security.UploadPathGuard.isAllowed(filePath)) {
                log.warn("Rejected client-supplied file path outside the upload whitelist: {}", fileName);
                return String.format("[用户上传的文件不可用: %s（路径不在允许的上传目录内）]\n\n%s", fileName, message);
            }
            return String.format("[用户上传了文件: %s, 路径: %s]\n\n%s", fileName, filePath, message);
        }
        return message;
    }
}
