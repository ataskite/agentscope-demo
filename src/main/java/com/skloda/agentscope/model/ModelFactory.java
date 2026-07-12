package com.skloda.agentscope.model;

import io.agentscope.core.formatter.Formatter;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ModelCreationContext;
import io.agentscope.core.model.ModelRegistry;
import io.agentscope.extensions.model.dashscope.formatter.DashScopeChatFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Unified model creation entry point using AgentScope 2.0 {@link ModelRegistry}.
 * <p>
 * Replaces hand-written {@code DashScopeChatModel.builder()} calls scattered across
 * AgentFactory, CompositeAgentFactory, and HarnessAgentFactory. Resolves model IDs
 * like {@code "dashscope:qwen-plus"} or bare names (auto-prefixed with
 * {@code dashscope:} when no provider prefix is present).
 */
@Component
public class ModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ModelFactory.class);

    private final String apiKey;

    public ModelFactory(@Value("${agentscope.model.dashscope.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Creates a chat model via ModelRegistry.
     *
     * @param modelName      bare model name (e.g. "qwen-plus") or provider-prefixed
     *                       (e.g. "dashscope:qwen-plus"); auto-prefixed with
     *                       "dashscope:" if no ":" is present
     * @param streaming      whether to enable streaming
     * @param enableThinking whether to enable thinking/reasoning mode
     * @return resolved Model instance
     */
    public Model createModel(String modelName, boolean streaming, boolean enableThinking) {
        String resolvedId = resolveModelId(modelName);

        ModelCreationContext context = ModelCreationContext.builder()
                .apiKey(resolveApiKey())
                .stream(streaming)
                .enableThinking(enableThinking)
                .component(Formatter.class, new DashScopeChatFormatter())
                .build();

        Model model = ModelRegistry.resolve(resolvedId, context);
        log.debug("Created model: {} (resolved={}, stream={}, thinking={})",
                modelName, resolvedId, streaming, enableThinking);
        return model;
    }

    /**
     * Returns the DashScope API key (resolved from config or env var).
     * Used by components that need the key directly (e.g. BailianLongTermMemory).
     */
    public String getApiKey() {
        return resolveApiKey();
    }

    /**
     * Auto-prefixes bare model names with "dashscope:" when no provider prefix is present.
     * <p>
     * This is necessary because {@code deepseek-v4-flash} does not match the
     * DashScopeModelProvider's {@code qwen.+} pattern — it requires the
     * {@code dashscope:} prefix to resolve correctly.
     */
    static String resolveModelId(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "dashscope:qwen-plus";
        }
        if (modelName.contains(":")) {
            return modelName;
        }
        return "dashscope:" + modelName;
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String envKey = System.getenv("DASHSCOPE_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey;
        }
        // ModelRegistry/DashScopeModelProvider will also check DASHSCOPE_API_KEY env,
        // so returning null here is safe — the provider handles the fallback.
        return null;
    }
}
