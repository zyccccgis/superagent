CREATE TABLE IF NOT EXISTS mcp_server_config (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  server_name VARCHAR(64) NOT NULL COMMENT 'MCP 服务名',
  transport_type VARCHAR(32) NOT NULL COMMENT '传输类型: SSE / STREAMABLE_HTTP',
  base_url VARCHAR(512) NOT NULL COMMENT 'MCP 服务基础地址',
  endpoint VARCHAR(256) NOT NULL COMMENT 'SSE 或 Streamable HTTP 端点',
  headers_json TEXT NULL COMMENT '请求头 JSON',
  request_timeout_seconds INT NOT NULL DEFAULT 30 COMMENT '请求超时时间秒',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN' COMMENT '运行状态',
  last_error TEXT NULL COMMENT '最后一次连接错误',
  last_connected_at DATETIME(3) NULL COMMENT '最后连接成功时间',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_server_name (server_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='MCP 服务配置表';
