package com.skloda.agentscope.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebSearchToolTest {

    @Test
    void searchWebRejectsBlankQueryWithoutNetworkCall() {
        WebSearchTool tool = new WebSearchTool();

        assertEquals("搜索关键词不能为空", tool.searchWeb("  ", 5));
    }

    @Test
    void weatherRejectsBlankCityWithoutNetworkCall() {
        WebSearchTool tool = new WebSearchTool();

        assertEquals("城市名称不能为空", tool.getWeather(""));
    }

    @Test
    void stockRejectsBlankSymbolWithoutNetworkCall() {
        WebSearchTool tool = new WebSearchTool();

        assertEquals("股票代码不能为空", tool.getStockPrice(" "));
    }

    @Test
    void weatherDelegatesToSearchWithWeatherQuery() {
        RecordingWebSearchTool tool = new RecordingWebSearchTool();

        String result = tool.getWeather("北京");

        assertEquals("query=北京 今天天气,limit=3", result);
    }

    @Test
    void stockDelegatesToSearchWithStockQuery() {
        RecordingWebSearchTool tool = new RecordingWebSearchTool();

        String result = tool.getStockPrice("AAPL");

        assertEquals("query=AAPL 股票价格 今天,limit=3", result);
    }

    @Test
    void newsUsesDefaultTopicAndLimit() {
        RecordingWebSearchTool tool = new RecordingWebSearchTool();

        String result = tool.getNews(null, null);

        assertEquals("query=今日最新新闻,limit=5", result);
    }

    private static class RecordingWebSearchTool extends WebSearchTool {
        @Override
        public String searchWeb(String query, Integer limit) {
            return "query=" + query + ",limit=" + limit;
        }
    }
}
