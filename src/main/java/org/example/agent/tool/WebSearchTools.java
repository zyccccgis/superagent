package org.example.agent.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 外网查询工具
 * 默认关闭，仅支持只读 GET 请求，并阻止访问 localhost/内网地址。
 */
@Component
@ConditionalOnProperty(prefix = "web.tool", name = "enabled", havingValue = "true")
@ManagedTool(
        name = "web_fetch",
        displayName = "网页查询",
        description = "访问公开网页或公开 HTTP API",
        riskLevel = "MEDIUM",
        instruction = "当用户需要访问公开网页或公开 HTTP API 时使用。禁止尝试访问 localhost、本机服务或内网地址。",
        order = 60
)
public class WebSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchTools.class);
    private static final int DEFAULT_MAX_RESPONSE_CHARS = 12000;

    public static final String TOOL_FETCH_WEB_PAGE = "fetchWebPage";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Value("${web.tool.timeout-seconds:10}")
    private int timeoutSeconds;

    @Value("${web.tool.max-response-chars:12000}")
    private int maxResponseChars;

    private HttpClient httpClient;

    @PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
        logger.info("✅ WebSearchTools 初始化成功, timeout: {}s, maxResponseChars: {}",
                timeoutSeconds, maxResponseChars);
    }

    @Tool(description = "Fetch a public web page or JSON API using HTTP GET. " +
            "Only http/https public internet addresses are allowed. " +
            "This tool is read-only and blocks localhost or private network targets.")
    public String fetchWebPage(
            @ToolParam(description = "A public http or https URL to fetch")
            String url) {
        long startedAt = System.currentTimeMillis();
        try {
            URI uri = validatePublicUri(url);
            logger.info("执行外网查询: {}", uri);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "SuperBizAgent/1.0")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body() == null ? "" : response.body();
            boolean truncated = false;
            int limit = Math.max(1, maxResponseChars > 0 ? maxResponseChars : DEFAULT_MAX_RESPONSE_CHARS);
            if (body.length() > limit) {
                body = body.substring(0, limit);
                truncated = true;
            }

            FetchOutput output = new FetchOutput();
            output.success = true;
            output.url = uri.toString();
            output.statusCode = response.statusCode();
            output.contentType = response.headers().firstValue("content-type").orElse(null);
            output.headers = extractSelectedHeaders(response);
            output.body = body;
            output.truncated = truncated;
            output.elapsedMs = System.currentTimeMillis() - startedAt;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            logger.error("执行外网查询失败", e);
            return buildError(url, e.getMessage());
        }
    }

    private URI validatePublicUri(String rawUrl) throws URISyntaxException, UnknownHostException {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL 不能为空");
        }

        URI uri = new URI(rawUrl.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅允许 http 或 https 协议");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL 缺少有效 host");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new IllegalArgumentException("禁止访问 localhost");
        }

        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            if (isPrivateOrLocalAddress(address)) {
                throw new IllegalArgumentException("禁止访问内网或本地地址: " + address.getHostAddress());
            }
        }
        return uri;
    }

    private boolean isPrivateOrLocalAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address.getHostAddress());
    }

    private boolean isUniqueLocalIpv6(String hostAddress) {
        String normalized = hostAddress.toLowerCase(Locale.ROOT);
        return normalized.startsWith("fc") || normalized.startsWith("fd");
    }

    private Map<String, String> extractSelectedHeaders(HttpResponse<String> response) {
        Map<String, String> headers = new HashMap<>();
        List<String> allowed = List.of("content-type", "content-length", "cache-control", "server");
        for (String key : allowed) {
            response.headers().firstValue(key).ifPresent(value -> headers.put(key, value));
        }
        return headers;
    }

    private String buildError(String url, String errorMessage) {
        try {
            ErrorOutput output = new ErrorOutput();
            output.success = false;
            output.url = url;
            output.error = errorMessage;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (IOException e) {
            return String.format("{\"success\":false,\"url\":\"%s\",\"error\":\"%s\"}", url, errorMessage);
        }
    }

    private static class FetchOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("url")
        public String url;
        @JsonProperty("status_code")
        public int statusCode;
        @JsonProperty("content_type")
        public String contentType;
        @JsonProperty("headers")
        public Map<String, String> headers;
        @JsonProperty("body")
        public String body;
        @JsonProperty("truncated")
        public boolean truncated;
        @JsonProperty("elapsed_ms")
        public long elapsedMs;
        @JsonProperty("executed_at")
        public String executedAt;
    }

    private static class ErrorOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("url")
        public String url;
        @JsonProperty("error")
        public String error;
        @JsonProperty("executed_at")
        public String executedAt;
    }
}
