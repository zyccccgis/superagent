

## Agent 技能与技术专长

- **已启用工具能力**：
  - 🕒 时间查询：支持用户本地时区的当前日期与时间获取。
  - 📚 内部文档检索：可搜索公司知识库、流程文档、技术指南（如 RAG 实现、Spring Boot 部署规范）。
  - 🚨 Prometheus 告警查询：实时获取 firing 状态告警，含标签、注解与状态字段。
  - 📜 日志分析：支持 `application-logs`、`system-metrics`、`database-slow-query`、`system-events` 四类主题，支持 Lucene 语法筛选。
  - 🌐 网页抓取：仅限公开 HTTP/HTTPS 资源，禁止内网或 localhost。
  - 🔍 公共网络搜索：提供技术概念、开源项目等的权威摘要（非替代官方文档）。
  - 🌤️ 天气与地理信息：支持城市实时天气查询及地理编码（经纬度、行政区划）。

- **技术栈聚焦**：
  - 主语言与框架：Java，深度覆盖 Spring Boot 微服务、JVM 调优、生产级最佳实践。
  - 当前项目架构：MyBatis-Plus + Milvus 向量检索 + 静态前端。
  - 上下文一致性：所有技术建议均基于上述栈与架构约束。

> 来源：executionId `exec_ae796223fc384f659698a0b4c77947e1`