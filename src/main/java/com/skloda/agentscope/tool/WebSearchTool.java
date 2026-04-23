package com.skloda.agentscope.tool;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Web search tool using Tavily API.
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    @Value("${tavily.api-key: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}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Tool(name = "web_search", description = "搜索网络信息。用于获取实时新闻、天气、股票价格、当前事件等最新信息。")
    public String searchWeb(
            @ToolParam(name = "query", description = "搜索关键词或问题") String query,
            @ToolParam(name = "limit", description = "返回结果数量，默认5条，最多10条") Integer limit
    ) {
        if (query == null || query.isBlank()) {
            return "搜索关键词不能为空";
        }

        int searchLimit = (limit != null && limit > 0 && limit <= 10) ? limit : 5;

        try {
            // Build Tavily API request
            String url = "https://api.tavily.com/search";

            Map<String, Object> requestBody = Map.of(
                    "api_key", apiKey,
                    "query", query,
                    "max_results", searchLimit,
                    "search_depth", "basic",
                    "include_answer", false,
                    "include_raw_content", false
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);

            if (response == null) {
                return "搜索失败：未获取到响应";
            }

            // Parse search results
            StringBuilder result = new StringBuilder();
            result.append("## 搜索结果：").append(query).append("\n\n");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");

            if (results == null || results.isEmpty()) {
                return "未找到相关结果";
            }

            for (int i = 0; i < results.size(); i++) {
                Map<String, Object> item = results.get(i);
                String title = (String) item.get("title");
                String url_link = (String) item.get("url");
                String content = (String) item.get("content");
                Number score = (Number) item.get("score");

                result.append(String.format("**%d. [%s](%s)**\n", i + 1, title, url_link));
                result.append(String.format("   相关性: %.2f\n", score != null ? score.doubleValue() : 0.0));

                // Truncate content if too long
                if (content != null) {
                    String truncated = content.length() > 200 ? content.substring(0, 200) + "..." : content;
                    result.append("   ").append(truncated).append("\n");
                }
                result.append("\n");
            }

            log.info("Web search completed: {} results", results.size());
            return result.toString();

        } catch (Exception e) {
            log.error("Web search failed for query: {}", query, e);
            return "搜索失败：" + e.getMessage();
        }
    }

    @Tool(name = "get_current_weather", description = "获取指定城市的当前天气信息")
    public String getWeather(
            @ToolParam(name = "city", description = "城市名称，例如：北京、上海、纽约") String city
    ) {
        if (city == null || city.isBlank()) {
            return "城市名称不能为空";
        }

        // Use web search for weather information
        return searchWeb(city + " 今天天气", 3);
    }

    @Tool(name = "get_stock_price", description = "获取股票的当前价格信息")
    public String getStockPrice(
            @ToolParam(name = "symbol", description = "股票代码或公司名称，例如：AAPL、腾讯、茅台") String symbol
    ) {
        if (symbol == null || symbol.isBlank()) {
            return "股票代码不能为空";
        }

        // Use web search for stock information
        return searchWeb(symbol + " 股票价格 今天", 3);
    }

    @Tool(name = "get_news", description = "获取最新新闻信息")
    public String getNews(
            @ToolParam(name = "topic", description = "新闻主题，可选，例如：科技、财经、体育") String topic,
            @ToolParam(name = "limit", description = "返回结果数量，默认5条") Integer limit
    ) {
        String query = (topic != null && !topic.isBlank()) ? topic + " 最新新闻" : "今日最新新闻";
        return searchWeb(query, limit != null ? limit : 5);
    }
}
