

## CPU 高占用标准排查指南

- **核心根因模式**：
  - 线程泄漏（如未关闭的 `ExecutorService`、`ThreadLocal` 持有对象）
  - 同步阻塞或死循环（如无超时的 `wait()`、`while(true)`、数据库连接池耗尽后的重试风暴）
  - 频繁 Full GC（高线程数 + 高 CPU 常伴随 GC 压力，即使未触发显式 GC 告警）
- **关键诊断信号**：
  - Java 进程 PID 1 线程数持续 >120（常规 Spring Boot 应用范围为 50–120）
  - 多服务 CPU/内存/响应延迟告警时间高度重叠（如 `payment-service` CPU >90%、`order-service` 内存 91%、`user-service` 响应 4.2s），提示共享资源瓶颈（如 DB 或缓存雪崩）
- **排除项参考**：
  - 若 `system-events` 中无 OOMKilled、Pod 重启等真实事件（仅占位符日志），可暂排除容器被 Kill 导致的 CPU 波动，聚焦应用层分析。
- **来源依据**：executionId `exec_01670305954f4c93a9566dbf7631a31c`。