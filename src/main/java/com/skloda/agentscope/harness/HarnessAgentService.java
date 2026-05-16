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
    private final String apiKey;
    private final ConcurrentHashMap<String, HarnessAgent> agentCache = new ConcurrentHashMap<>();

    public HarnessAgentService(AgentConfigService configService,
                               @Value("${agentscope.model.dashscope.api-key:}") String apiKey) {
        this.configService = configService;
        this.apiKey = apiKey;
    }

    public Flux<Map<String, Object>> createStreamFlux(String agentId, String message,
                                                       String filePath, String fileName,
                                                       String sessionId) {
        try {
            HarnessAgent agent = getOrCreateAgent(agentId);

            String actualMessage = prependFileInfo(filePath, fileName, message);
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .content(TextBlock.builder().text(actualMessage).build())
                    .build();

            RuntimeContext ctx = RuntimeContext.builder()
                    .sessionId(sessionId != null ? sessionId : "default")
                    .userId("demo-user")
                    .build();

            HarnessRuntime runtime = new HarnessRuntime(agent);
            return runtime.stream(userMsg, ctx);
        } catch (Exception e) {
            log.error("Failed to create harness stream for agent: {}", agentId, e);
            return Flux.just(Map.of("type", "error", "message", e.getMessage()));
        }
    }

    private HarnessAgent getOrCreateAgent(String agentId) throws Exception {
        return agentCache.computeIfAbsent(agentId, id -> {
            try {
                AgentConfig config = configService.getAgentConfig(id);
                String effectiveKey = resolveApiKey();
                return HarnessAgentFactory.create(config, effectiveKey);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create HarnessAgent: " + id, e);
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
            return String.format("[用户上传了文件: %s, 路径: %s]\n\n%s", fileName, filePath, message);
        }
        return message;
    }
}
