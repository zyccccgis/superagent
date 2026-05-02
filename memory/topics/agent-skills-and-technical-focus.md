

## Agent 技能与技术聚焦

- **已启用工具能力**：
  - 🕒 时间查询：支持用户本地时区的当前日期与时间。
  - 📚 内部文档检索：可精准搜索公司知识库，覆盖 RAG 实现规范、Spring Boot 生产配置模板、JVM 调优 checklist、Milvus 向量索引策略等（executionId: exec_1e1a0ade63a9491fa74476f5e23c437b）。
  - 🚨 Prometheus 告警查询：实时拉取 firing 状态告警，含标签、注解与触发值，用于根因定位。
  - 📜 日志分析：支持四大主题日志（`application-logs`、`system-metrics`、`database-slow-query`、`system-events`），支持 Lucene 语法筛选，适用于错误追踪、性能瓶颈与 OOM 分析。
  - 🌐 网页抓取：安全获取公开网页或 API（如 Spring 官方文档、GitHub README、Maven 仓库信息），禁止访问内网或 localhost。
  - 🔍 公共网络搜索：提供 Java/云原生/向量数据库等技术概念的权威摘要（标注“公开信息摘要”，不替代官方文档）。
  - 🌤️ 天气与地理信息：支持城市天气查询与地理编码（经纬度、行政区划），可用于 GIS 集成或测试环境地域模拟。

- **长期技术聚焦**：
  - ✅ 当前项目架构：Spring Boot 3 + MyBatis-Plus + Milvus 向量引擎 + 静态前端。
  - ✅ Java 生产最佳实践：内存泄漏防控、GC 日志解读、线程池监控、Actuator 健康检查定制。
  - ✅ 工具链协同逻辑：建立「告警 → 日志 → JVM 指标」闭环排查路径。