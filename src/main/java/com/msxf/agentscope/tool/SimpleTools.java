package com.msxf.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class SimpleTools {

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

    @Tool(name = "calculate_sum", description = "Calculate the sum of two numbers")
    public double calculateSum(
            @ToolParam(name = "a", description = "First number") double a,
            @ToolParam(name = "b", description = "Second number") double b) {
        return a + b;
    }

    @Tool(name = "get_weather", description = "Get weather information for a city")
    public String getWeather(
            @ToolParam(name = "city", description = "English Name of the city，first letter is uppercase") String city) throws InterruptedException {
        Map<String, String> weatherData = new HashMap<>();
        weatherData.put("Beijing", "Sunny, 25\u00B0C");
        weatherData.put("Shanghai", "Cloudy, 22\u00B0C");
        weatherData.put("Guangzhou", "Rainy, 28\u00B0C");
        weatherData.put("Shenzhen", "Sunny, 30\u00B0C");
        return weatherData.getOrDefault(city, "Weather information not available for " + city);
    }
}
