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
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * MySQL 工具
 * 提供受控的查询和写入能力，默认关闭，通过 mysql.tool.enabled 显式开启。
 */
@Component
@ConditionalOnProperty(prefix = "mysql.tool", name = "enabled", havingValue = "true")
public class MySqlTools {

    private static final Logger logger = LoggerFactory.getLogger(MySqlTools.class);

    public static final String TOOL_EXECUTE_QUERY = "executeMySqlQuery";
    public static final String TOOL_EXECUTE_UPDATE = "executeMySqlUpdate";

    private static final Set<String> QUERY_PREFIXES = Set.of("select", "show", "describe", "desc", "explain");
    private static final Set<String> UPDATE_PREFIXES = Set.of("insert", "update", "delete");
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "drop", "truncate", "alter", "create", "rename", "grant", "revoke"
    );

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    @Value("${mysql.tool.url}")
    private String jdbcUrl;

    @Value("${mysql.tool.username}")
    private String username;

    @Value("${mysql.tool.password:}")
    private String password;

    @Value("${mysql.tool.query-timeout-seconds:10}")
    private int queryTimeoutSeconds;

    @PostConstruct
    public void init() {
        logger.info("✅ MySqlTools 初始化成功, url: {}, timeout: {}s", jdbcUrl, queryTimeoutSeconds);
    }

    @Tool(description = "Execute a read-only SQL query against MySQL. " +
            "Only SELECT/SHOW/DESCRIBE/EXPLAIN statements are allowed. " +
            "Use this when you need to inspect business data, table structure, or query results.")
    public String executeMySqlQuery(
            @ToolParam(description = "A read-only SQL statement. Must start with SELECT, SHOW, DESCRIBE, DESC, or EXPLAIN.")
            String sql) {
        try {
            validateSql(sql, QUERY_PREFIXES, true);

            logger.info("执行 MySQL 只读查询");
            long startedAt = System.currentTimeMillis();
            try (Connection connection = openConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setQueryTimeout(queryTimeoutSeconds);

                try (ResultSet resultSet = statement.executeQuery()) {
                    ResultSetMetaData metaData = resultSet.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<Map<String, Object>> rows = new ArrayList<>();

                    while (resultSet.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                        }
                        rows.add(row);
                    }

                    QueryOutput output = new QueryOutput();
                    output.success = true;
                    output.operation = "query";
                    output.sql = sql;
                    output.rowCount = rows.size();
                    output.elapsedMs = System.currentTimeMillis() - startedAt;
                    output.rows = rows;
                    output.executedAt = Instant.now().toString();
                    return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
                }
            }
        } catch (Exception e) {
            logger.error("执行 MySQL 查询失败", e);
            return buildError("query", sql, e.getMessage());
        }
    }

    @Tool(description = "Execute a controlled data modification SQL statement against MySQL. " +
            "Only INSERT, UPDATE, and DELETE are allowed. " +
            "DDL such as DROP/TRUNCATE/ALTER/CREATE is blocked.")
    public String executeMySqlUpdate(
            @ToolParam(description = "A data modification SQL statement. Must start with INSERT, UPDATE, or DELETE.")
            String sql) {
        try {
            validateSql(sql, UPDATE_PREFIXES, false);

            logger.info("执行 MySQL 数据变更");
            long startedAt = System.currentTimeMillis();
            try (Connection connection = openConnection();
                 Statement statement = connection.createStatement()) {
                statement.setQueryTimeout(queryTimeoutSeconds);
                int affectedRows = statement.executeUpdate(sql);

                UpdateOutput output = new UpdateOutput();
                output.success = true;
                output.operation = "update";
                output.sql = sql;
                output.affectedRows = affectedRows;
                output.elapsedMs = System.currentTimeMillis() - startedAt;
                output.executedAt = Instant.now().toString();
                output.message = "SQL executed successfully";
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            }
        } catch (Exception e) {
            logger.error("执行 MySQL 更新失败", e);
            return buildError("update", sql, e.getMessage());
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void validateSql(String sql, Set<String> allowedPrefixes, boolean readOnly) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL 不能为空");
        }

        String normalized = sql.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(";")) {
            throw new IllegalArgumentException("不允许执行多条 SQL");
        }

        boolean allowed = allowedPrefixes.stream().anyMatch(normalized::startsWith);
        if (!allowed) {
            throw new IllegalArgumentException("SQL 类型不被允许");
        }

        for (String keyword : DANGEROUS_KEYWORDS) {
            if (normalized.matches(".*\\b" + keyword + "\\b.*")) {
                throw new IllegalArgumentException("检测到高危 SQL 关键字: " + keyword);
            }
        }

        if (readOnly && normalized.matches("^select\\s+.*\\binto\\b.*")) {
            throw new IllegalArgumentException("不允许执行带 INTO 的查询");
        }
    }

    private String buildError(String operation, String sql, String errorMessage) {
        try {
            ErrorOutput output = new ErrorOutput();
            output.success = false;
            output.operation = operation;
            output.sql = sql;
            output.error = errorMessage;
            output.executedAt = Instant.now().toString();
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
        } catch (Exception e) {
            return String.format("{\"success\":false,\"operation\":\"%s\",\"error\":\"%s\"}", operation, errorMessage);
        }
    }

    private static class QueryOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("operation")
        public String operation;
        @JsonProperty("sql")
        public String sql;
        @JsonProperty("row_count")
        public int rowCount;
        @JsonProperty("elapsed_ms")
        public long elapsedMs;
        @JsonProperty("rows")
        public List<Map<String, Object>> rows;
        @JsonProperty("executed_at")
        public String executedAt;
    }

    private static class UpdateOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("operation")
        public String operation;
        @JsonProperty("sql")
        public String sql;
        @JsonProperty("affected_rows")
        public int affectedRows;
        @JsonProperty("elapsed_ms")
        public long elapsedMs;
        @JsonProperty("executed_at")
        public String executedAt;
        @JsonProperty("message")
        public String message;
    }

    private static class ErrorOutput {
        @JsonProperty("success")
        public boolean success;
        @JsonProperty("operation")
        public String operation;
        @JsonProperty("sql")
        public String sql;
        @JsonProperty("error")
        public String error;
        @JsonProperty("executed_at")
        public String executedAt;
    }
}
