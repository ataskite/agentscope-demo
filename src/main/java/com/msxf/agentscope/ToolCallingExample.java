package com.msxf.agentscope;

import com.msxf.agentscope.util.ExampleUtils;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.formatter.dashscope.DashScopeChatFormatter;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.core.tool.Toolkit;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * ToolCallingExample - Demonstrates Agent's ability to use tools.
 * <p>
 * This example shows:
 * <ul>
 *   <li>Creating custom tools using @Tool annotation</li>
 *   <li>Registering tools with an agent</li>
 *   <li>Agent automatically selecting and using tools</li>
 * </ul>
 */
public class ToolCallingExample {

    /**
     * Simple tools for demonstration.
     */
    public static class SimpleTools {

        /**
         * Get current date and time in a specific timezone.
         */
        @Tool(name = "get_current_time", description = "Get the current date and time in a specific timezone")
        public String getCurrentTime(
                @ToolParam(name = "timezone", description = "Timezone name, e.g., 'Asia/Shanghai', 'America/New_York'") String timezone) {
            try {
                ZoneId zoneId = ZoneId.of(timezone);
                LocalDateTime now = LocalDateTime.now(zoneId);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return String.format("Current time in %s: %s", timezone, now.format(formatter));
            } catch (Exception e) {
                return "Error: Invalid timezone. Try 'Asia/Shanghai' or 'America/New_York'";
            }
        }

        /**
         * Calculate the sum of two numbers.
         */
        @Tool(name = "calculate_sum", description = "Calculate the sum of two numbers")
        public double calculateSum(
                @ToolParam(name = "a", description = "First number") double a,
                @ToolParam(name = "b", description = "Second number") double b) {
            return a + b;
        }

        /**
         * Get weather information (simulated).
         */
        @Tool(name = "get_weather", description = "Get weather information for a city")
        public String getWeather(
                @ToolParam(name = "city", description = "Name of the city") String city) {

            // Simulated weather data
            Map<String, String> weatherData = new HashMap<>();
            weatherData.put("Beijing", "Sunny, 25°C");
            weatherData.put("Shanghai", "Cloudy, 22°C");
            weatherData.put("Guangzhou", "Rainy, 28°C");
            weatherData.put("Shenzhen", "Sunny, 30°C");

            return weatherData.getOrDefault(city, "Weather information not available for " + city);
        }
    }

    public static void main(String[] args) throws Exception {
        // Print welcome message
        ExampleUtils.printWelcome(
                "Tool Calling Example",
                "This example demonstrates Agent's ability to use tools.\n\n" +
                        "Available tools:\n" +
                        "  - get_current_time: Get current date and time in a timezone\n" +
                        "  - calculate_sum: Calculate sum of two numbers\n" +
                        "  - get_weather: Get weather for a city (Beijing, Shanghai, Guangzhou, Shenzhen)\n\n" +
                        "Try asking:\n" +
                        "  \"What time is it in Shanghai?\"\n" +
                        "  \"What's 123 plus 456?\"\n" +
                        "  \"What's the weather in Beijing?\"");

        // Get API key
        String apiKey = ExampleUtils.getDashScopeApiKey();

        // Create toolkit and register tools
        Toolkit toolkit = new Toolkit();
        toolkit.registerTool(new SimpleTools());

        // Create Agent with tools
        ReActAgent agent =
                ReActAgent.builder()
                        .name("ToolAgent")
                        .sysPrompt("You are a helpful AI assistant with access to various tools. " +
                                "Use the appropriate tools when needed to answer questions accurately. " +
                                "Always explain what you're doing when using tools.")
                        .model(
                                DashScopeChatModel.builder()
                                        .apiKey(apiKey)
                                        .modelName("qwen-plus")
                                        .stream(true)
                                        .enableThinking(true)
                                        .formatter(new DashScopeChatFormatter())
                                        .build())
                        .memory(new InMemoryMemory())
                        .toolkit(toolkit)
                        .build();

        // Start interactive chat
        ExampleUtils.startChat(agent);
    }
}
