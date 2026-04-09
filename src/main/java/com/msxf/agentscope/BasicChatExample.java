package com.msxf.agentscope;

import com.msxf.agentscope.util.ExampleUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.tool.Toolkit;

/**
 * BasicChatExample - The simplest Agent conversation example.
 * <p>
 * This example demonstrates:
 * <ul>
 *   <li>Creating a ReActAgent with minimal configuration</li>
 *   <li>Configuring DashScope chat model with streaming support</li>
 *   <li>Interactive chat loop with real-time response streaming</li>
 * </ul>
 */
public class BasicChatExample {

    public static void main(String[] args) throws Exception {
        // Print welcome message
        ExampleUtils.printWelcome(
                "Basic Chat Example",
                "This example demonstrates the simplest Agent setup.\n" +
                        "You'll chat with an AI assistant powered by DashScope.\n\n" +
                        "Features:\n" +
                        "  - ReAct reasoning pattern\n" +
                        "  - Streaming responses\n" +
                        "  - In-memory conversation history");

        // Get API key (from environment or interactive input)
        String apiKey = ExampleUtils.getDashScopeApiKey();

        // Create Agent with minimal configuration
        ReActAgent agent =
                ReActAgent.builder()
                        .name("Assistant")
                        .sysPrompt("You are a helpful AI assistant. Be friendly and concise.")
                        .model(
                                DashScopeChatModel.builder()
                                        .apiKey(apiKey)
                                        .modelName("qwen-plus")
                                        .stream(true)
                                        .enableThinking(true)
                                        .formatter(new DashScopeChatFormatter())
                                        .defaultOptions(
                                                GenerateOptions.builder()
                                                        .thinkingBudget(1024)
                                                        .build())
                                        .build())
                        .memory(new InMemoryMemory())
                        .toolkit(new Toolkit())
                        .build();

        // Start interactive chat
        ExampleUtils.startChat(agent);
    }
}
