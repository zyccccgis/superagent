

## 可用工具清单

- **时间工具**：`getCurrentDateTime` — 获取当前本地时间。
- **内部文档检索**：`queryInternalDocs` — 搜索公司内部知识库、流程文档、技术指南等。
- **Prometheus 告警查询**：`queryPrometheusAlerts` — 查看当前正在触发的系统告警。
- **日志查询（两步式）**：
  - `getAvailableLogTopics`：枚举可用日志主题（如 `application-logs`, `system-metrics`, `database-slow-query`, `system-events`）；
  - `queryLogs`：按主题与条件查询具体日志内容。
- **网页查询**：`fetchWebPage` — 抓取公开网页或 HTTP API 内容；**禁止访问内网地址、localhost 或私有网络资源**。

> 所有工具调用均受统一安全策略约束，且 Agent 会根据任务自动选择最合适的工具组合。该能力集基于 executionId: `exec_27da4d7acacd44a5b594e7ef8a932f82` 确认。