CREATE TABLE IF NOT EXISTS agent_trace (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  trace_id VARCHAR(80) NOT NULL COMMENT 'Trace ID',
  session_id VARCHAR(128) NULL COMMENT '会话 ID',
  user_input TEXT NULL COMMENT '用户输入',
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态',
  start_time DATETIME(3) NOT NULL COMMENT '开始时间',
  end_time DATETIME(3) NULL COMMENT '结束时间',
  duration_ms BIGINT NULL COMMENT '耗时毫秒',
  model_name VARCHAR(128) NULL COMMENT '模型名称',
  error_message TEXT NULL COMMENT '错误信息',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trace_id (trace_id),
  KEY idx_session_time (session_id, start_time),
  KEY idx_status_time (status, start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 执行 Trace 表';

CREATE TABLE IF NOT EXISTS agent_trace_step (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  trace_id VARCHAR(80) NOT NULL COMMENT 'Trace ID',
  step_type VARCHAR(64) NOT NULL COMMENT '步骤类型',
  step_name VARCHAR(128) NOT NULL COMMENT '步骤名称',
  status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT '状态',
  input_summary MEDIUMTEXT NULL COMMENT '输入摘要',
  output_summary MEDIUMTEXT NULL COMMENT '输出摘要',
  start_time DATETIME(3) NOT NULL COMMENT '开始时间',
  end_time DATETIME(3) NULL COMMENT '结束时间',
  duration_ms BIGINT NULL COMMENT '耗时毫秒',
  error_message TEXT NULL COMMENT '错误信息',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_trace_time (trace_id, start_time),
  KEY idx_step_type (step_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Trace 步骤表';

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  trace_id VARCHAR(80) NOT NULL COMMENT 'Trace ID',
  step_id BIGINT NULL COMMENT '步骤 ID',
  tool_name VARCHAR(128) NOT NULL COMMENT '工具名',
  tool_source VARCHAR(32) NOT NULL COMMENT '工具来源',
  request_json MEDIUMTEXT NULL COMMENT '请求参数',
  response_summary MEDIUMTEXT NULL COMMENT '响应摘要',
  status VARCHAR(32) NOT NULL COMMENT '状态',
  duration_ms BIGINT NULL COMMENT '耗时毫秒',
  error_message TEXT NULL COMMENT '错误信息',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_trace_id (trace_id),
  KEY idx_step_id (step_id),
  KEY idx_tool_name (tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent 工具调用表';
