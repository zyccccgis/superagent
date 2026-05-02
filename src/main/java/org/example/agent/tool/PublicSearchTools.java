package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 公共搜索工具。
 * 参考 Spring AI Alibaba tool-calling 的“搜索类工具”边界，但直接落到本项目 @Tool 体系。
 */
@Component
@ManagedTool(
        name = "public_search",
        displayName = "公共搜索",
        description = "查询公开互联网摘要、相关主题和参考链接",
        riskLevel = "MEDIUM",
        instruction = "当用户需要查询公开事实、产品、技术资料或外部信息时使用。结果来自公开搜索摘要，回答时需要说明不等同于权威来源。",
        order = 70
)
public class PublicSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(PublicSearchTools.class);
    private static final String DUCKDUCKGO_API = "https://api.duckduckgo.com/";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Value("${search.tool.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${search.tool.max-results:5}")
    private int maxResults;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        logger.info("PublicSearchTools 初始化成功, timeout: {}s, maxResults: {}", timeoutSeconds, maxResults);
    }

    @Tool(description = "Search public web summary and related topics. Use this for public facts, external references, product information, or technology lookup.")
    public String searchPublicWeb(
            @ToolParam(description = "Search query, preferably concise and specific")
            String query) {
        long startedAt = System.currentTimeMillis();
        try {
            if (query == null || query.trim().isEmpty()) {
                throw new IllegalArgumentException("query 不能为空");
            }

            URI uri = URI.create(DUCKDUCKGO_API + "?q=" + encode(query.trim())
                    + "&format=json&no_html=1&skip_disambig=1");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "SuperBizAgent/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(response.body());

            SearchOutput output = new SearchOutput();
            output.success = response.statusCode() >= 200 && response.statusCode() < 300;
            output.query = query.trim();
            output.statusCode = response.statusCode();
            output.heading = text(root, "Heading");
            output.abstractText = text(root, "AbstractText");
            output.abstractUrl = text(root, "AbstractURL");
            output.answer = text(root, "Answer");
            output.results = relatedTopics(root.path("RelatedTopics"), Math.max(1, maxResults));
            output.elapsedMs = System.currentTimeMillis() - startedAt;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("公共搜索失败", e);
            return buildError(query, e.getMessage());
        }
    }

    private List<SearchResult> relatedTopics(JsonNode topics, int limit) {
        List<SearchResult> results = new ArrayList<>();
        collectTopics(topics, results, limit);
        return results;
    }

    private void collectTopics(JsonNode node, List<SearchResult> results, int limit) {
        if (!node.isArray() || results.size() >= limit) {
            return;
        }
        for (JsonNode item : node) {
            if (results.size() >= limit) {
                return;
            }
            if (item.has("Topics")) {
                collectTopics(item.path("Topics"), results, limit);
                continue;
            }
            String text = text(item, "Text");
            String url = text(item, "FirstURL");
            if (!text.isBlank() || !url.isBlank()) {
                SearchResult result = new SearchResult();
                result.title = text;
                result.url = url;
                results.add(result);
            }
        }
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String buildError(String query, String errorMessage) {
        try {
            ErrorOutput output = new ErrorOutput();
            output.success = false;
            output.query = query;
            output.error = errorMessage;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (IOException e) {
            return "{\"success\":false,\"error\":\"" + errorMessage + "\"}";
        }
    }

    private static class SearchOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("query")
        public String query;
        @JsonProperty("status_code")
        public int statusCode;
        @JsonProperty("heading")
        public String heading;
        @JsonProperty("abstract_text")
        public String abstractText;
        @JsonProperty("abstract_url")
        public String abstractUrl;
        @JsonProperty("answer")
        public String answer;
        @JsonProperty("results")
        public List<SearchResult> results;
        @JsonProperty("elapsed_ms")
        public long elapsedMs;
        @JsonProperty("executed_at")
        public String executedAt;
    }

    private static class SearchResult {
        @JsonProperty("title")
        public String title;
        @JsonProperty("url")
        public String url;
    }

    private static class ErrorOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("query")
        public String query;
        @JsonProperty("error")
        public String error;
        @JsonProperty("executed_at")
        public String executedAt;
    }
}
