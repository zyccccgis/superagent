

## Java 生产环境最佳实践

- **JVM 内存管理**：推荐启用 `-XX:+HeapDumpOnOutOfMemoryError`；堆外内存需结合 `NativeMemoryTracking` 监控；元空间应显式设置 `-XX:MaxMetaspaceSize`（如 256M），避免动态扩容引发 GC 不稳定。
- **GC 算法选型**：响应延迟敏感型微服务优先评估 ZGC（JDK 15+）或 G1（JDK 8u202+）；吞吐优先场景可选用 Parallel GC；避免在容器中使用未限制 `-Xmx` 的默认堆行为。
- **内存泄漏高发点**（Spring Boot 场景）：
  - 未清理的 `ThreadLocal` 变量（尤其在线程池复用中）；
  - 静态集合（如 `static Map`）长期持有业务对象引用；
  - 事件监听器/回调注册后未注销；
  - 热部署或模块化加载导致的 `ClassLoader` 泄漏。
- **MyBatis 性能关键项**：
  - N+1 查询必须通过 `@SelectProvider` 或 `resultMap` 中 `collection` 的 `fetchType="eager"` 显式控制；
  - 批量插入优先使用 `BatchExecutor` 而非 `<foreach>` 拼接 SQL；
  - 二级缓存需规避共享状态污染，建议按业务域隔离缓存命名空间。
- **Spring Boot 启动优化**：
  - 使用 `@ConditionalOnMissingBean` 细粒度控制自动装配，减少无用 Bean 初始化；
  - `@Async` 必须自定义线程池（禁止使用 `SimpleAsyncTaskExecutor`），并配置拒绝策略与队列容量。

> 来源：executionId `exec_873ba5a6bb774fec9357893428559fb4`