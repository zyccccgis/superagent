

## Extracted Memory - 2026-05-01T11:40:02.687138

- source: `exec_359745dd215e4900abe56644243e40ef`
  - user: 如何进行CPU
  - insight: 你好！你提到“如何进行CPU”，这个表述可能不够完整，我需要更多上下文来准确理解你的需求。以下是几种常见可能性，请你确认或补充： - ❓ **想查询当前系统的 CPU 使用率？** → 可以查 Prometheus 告警（如 `HighCPUUsage`）或系统指标日志。 - ❓ **想排查 CPU 高占用问题？** → 可结合 `system-metrics` 日志 + `application-logs` + `system-events` 分析（例如是否有 Pod 重启、GC 频繁、死循环等）。 - ❓ **想了解项目中与 CPU 相关的技术实践或监控配置？** → 可搜索内部文档（如监控告警规范、SRE 运维手册、性能调优指南）。 - ❓ **想在代码/部署中做 CPU 限制（如 Kubernetes limits/requests）？** → 需要参考容器编排规范或 SRE 标准化文档。 请告诉我你具体想做什么，例如：
- source: `exec_01670305954f4c93a9566dbf7631a31c`
  - user: 如何排查 CPU 高占用问题
  - insight: 感谢耐心等待！我们已获取关键诊断数据，以下是**精准定位结果与下一步行动建议**： --- ### 🔍 当前核心问题确认（高置信度） - ✅ **明确根因服务**：`payment-service`（pod `pod-payment-service-7d8f9c6b5-x2k4m`）CPU 持续 >90%（当前 92%），且已持续 **25 分钟**，属于严重异常。 - ✅ **进程级线索**：日志明确指出是 `java` 进程（PID 1）导致，线程数高达 **245 个**（远超常规 Spring Boot 应用的 50–120 范围），提示存在： - ❗ 线程泄漏（如未关闭的 `ExecutorService`、`ThreadLocal` 泄漏） - ❗ 同步阻塞或死循环（如 `while(true)`、无超时的 `wait()`、数据库连接池耗尽后重试风暴） - ❗ 频繁 Full GC（虽当前告警未显式报 GC，但高