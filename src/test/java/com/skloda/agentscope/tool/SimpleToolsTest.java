package com.skloda.agentscope.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleToolsTest {

    private final SimpleTools tools = new SimpleTools();

    @Test
    void calculateSumSupportsPositiveNegativeAndDecimals() {
        assertEquals(3.75, tools.calculateSum(1.25, 2.5));
        assertEquals(-5.0, tools.calculateSum(-2.0, -3.0));
    }

    @Test
    void getWeatherReturnsKnownCityForecast() throws InterruptedException {
        assertEquals("Sunny, 25°C", tools.getWeather("Beijing"));
    }

    @Test
    void getWeatherReturnsFallbackForUnknownCity() throws InterruptedException {
        assertEquals("Weather information not available for Paris", tools.getWeather("Paris"));
    }

    @Test
    void getCurrentTimeFormatsValidTimezone() {
        String result = tools.getCurrentTime("Asia/Shanghai");

        assertTrue(result.startsWith("Current time in Asia/Shanghai: "));
    }

    @Test
    void getCurrentTimeReturnsHelpfulErrorForInvalidTimezone() {
        assertEquals("Error: Invalid timezone. Try 'Asia/Shanghai' or 'America/New_York'",
                tools.getCurrentTime("Not/AZone"));
    }
}
