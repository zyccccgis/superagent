# SuperBizAgent

SuperBizAgent 是一个面向企业知识问答和智能运维诊断的 AI Agent 后端服务。项目基于 Spring Boot 3、Spring AI Alibaba、DashScope、Milvus 和 MySQL 构建，提供 RAG 知识库、流式对话、AIOps 任务、Agent 工具调用和记忆管理能力。

它不是一个单纯的聊天接口，而是一个可落地的 Agent 服务骨架：文档可以上传并向量化，运维告警可以进入诊断任务，Agent 可以调用指标、日志、内部文档、数据库和外部检索工具，执行过程也可以沉淀为记忆。

## 功能概览

| 模块 | 能力 |
| --- | --- |
| 智能对话 | 普通对话、SSE 流式对话、多轮会话、工具调用 |
| RAG 知识库 | 文档上传、文档切分、Embedding、Milvus 向量索引、TopK 召回 |
| AIOps 诊断 | 创建诊断任务、查询任务结果、结合指标/日志/文档进行分析 |
| Agent 工具 | 时间工具、内部文档检索、指标查询、日志查询、MySQL 查询、外网查询 |
| 记忆系统 | 长期记忆文件、短期执行记忆、记忆抽取、记忆压缩 |
| 本地控制台 | Spring Boot 静态页面，可直接访问 `http://localhost:9900` |

## 技术栈

| 技术 | 当前配置 |
| --- | --- |
| Java | 17 |
| Spring Boot | 3.2.0 |
| Spring AI | 1.1.0 |
| Spring AI Alibaba | 1.1.0.0-RC2 |
| DashScope SDK | 2.17.0 |
| Milvus Java SDK | 2.6.10 |
| MyBatis Plus | 3.5.7 |
| MySQL | 8.0.42 Docker 镜像 |
| Milvus | 2.5.10 Docker 镜像 |

## 系统架构

```text
Client / Web UI
      |
      v
Spring Boot API
      |
      +-- ChatController
      |     +-- ChatApplicationService
      |     +-- ChatStreamService
      |     +-- ChatSessionService
      |
      +-- RagController
      |     +-- RagDocumentService
      |     +-- DocumentChunkService
      |     +-- VectorEmbeddingService
      |     +-- VectorIndexService
      |     +-- RagRetrievalService
      |
      +-- AIOpsTaskService / AiOpsService
      |     +-- QueryMetricsTools
      |     +-- QueryLogsTools
      |     +-- InternalDocsTools
      |     +-- MySqlTools
      |     +-- WebSearchTools
      |
      +-- MemoryController
            +-- MemoryService
            +-- AgentExecutionMemoryService
            +-- MemoryMaintenanceService

External services:
DashScope, Milvus, MySQL, optional Prometheus / CLS / MCP
```

## 目录结构

```text
.
├── aiops-docs/                  # 示例运维知识文档
├── docker/mysql/init/           # MySQL 初始化脚本
├── docs/rag-api.md              # RAG API 详细说明
├── memory/                      # 本地长期记忆目录
├── src/main/java/org/example/
│   ├── agent/tool/              # Agent 工具
│   ├── client/                  # 外部客户端封装
│   ├── config/                  # 应用配置
│   ├── controller/              # HTTP API
│   ├── dto/                     # 请求/响应 DTO
│   ├── entity/                  # 数据库实体
│   ├── mapper/                  # MyBatis Plus Mapper
│   └── service/                 # 业务服务
├── src/main/resources/
│   ├── application.yml          # Spring Boot 配置
│   └── static/                  # 前端静态页面
├── uploads/                     # 上传的知识库文件
├── vector-database.yml          # MySQL + Milvus 本地依赖
└── pom.xml
```

## 环境准备

需要先安装：

- JDK 17
- Maven 3.8+
- Docker / Docker Compose
- DashScope API Key

设置 DashScope Key：

```bash
export DASHSCOPE_API_KEY="your-api-key"
```

如果不使用默认 MySQL 账号，可以覆盖数据源配置：

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/superbiz_agent?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
export SPRING_DATASOURCE_USERNAME="superbiz"
export SPRING_DATASOURCE_PASSWORD="superbiz1234"
```

## 快速启动

### 1. 启动基础依赖

```bash
docker-compose -f vector-database.yml up -d
```

该命令会启动：

| 服务 | 地址 |
| --- | --- |
| MySQL | `localhost:3306` |
| Milvus | `localhost:19530` |
| Attu | `http://localhost:8000` |
| MinIO Console | `http://localhost:9001` |

默认 MySQL 账号：

```text
database: superbiz_agent
username: superbiz
password: superbiz1234
root password: root1234
```

### 2. 启动应用

```bash
mvn spring-boot:run
```

默认服务地址：

```text
http://localhost:9900
```

Web 页面：

```text
http://localhost:9900
```

### 3. 检查依赖状态

```bash
curl http://localhost:9900/milvus/health
```

返回 `message: ok` 表示应用已经连上 Milvus。

## RAG 知识库使用

### 上传文档

当前支持 `txt` 和 `md` 文件。上传后会保存文件、写入 MySQL 元数据，并同步写入 Milvus 向量索引。

```bash
curl -X POST http://localhost:9900/api/rag/documents \
  -F "file=@aiops-docs/cpu_high_usage.md"
```

批量导入示例文档：

```bash
for file in aiops-docs/*.md; do
  curl -X POST http://localhost:9900/api/rag/documents -F "file=@${file}"
done
```

### 查询文档

```bash
curl "http://localhost:9900/api/rag/documents?page=1&pageSize=20&keyword=cpu"
```

### 测试召回

该接口只做向量检索，不调用大模型。

```bash
curl -X POST http://localhost:9900/api/rag/retrieve \
  -H "Content-Type: application/json" \
  -d '{"text":"CPU 使用率过高怎么排查","topK":5}'
```

更多字段和响应格式见 [docs/rag-api.md](docs/rag-api.md)。

## 对话接口

### 普通对话

```bash
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"Id":"demo-session","Question":"CPU 使用率过高应该怎么排查？"}'
```

### 流式对话

```bash
curl -N -X POST http://localhost:9900/api/chat_stream \
  -H "Content-Type: application/json" \
  -d '{"Id":"demo-session","Question":"请总结磁盘空间告警的处理流程"}'
```

### 会话查询和清理

```bash
curl http://localhost:9900/api/chat/session/demo-session
```

```bash
curl -X POST http://localhost:9900/api/chat/clear \
  -H "Content-Type: application/json" \
  -d '{"Id":"demo-session"}'
```

## AIOps 诊断任务

创建诊断任务：

```bash
curl -X POST http://localhost:9900/api/ai_ops/tasks \
  -H "Content-Type: application/json" \
  -d '{"alertName":"CPUHighUsage","severity":"critical","description":"prod-app-01 CPU 使用率持续高于 90%"}'
```

查询诊断任务：

```bash
curl http://localhost:9900/api/ai_ops/tasks/{taskId}
```

默认配置中 Prometheus 和 CLS 都开启了 Mock 模式，本地启动后可以先验证完整流程。接入真实环境时，需要关闭 Mock 并配置对应服务地址。

## 记忆管理接口

长期记忆文件：

```bash
curl "http://localhost:9900/api/memory/files?type=all"
```

执行记忆：

```bash
curl "http://localhost:9900/api/memory/executions?page=1&pageSize=10"
```

抽取长期记忆：

```bash
curl -X POST http://localhost:9900/api/memory/extract \
  -H "Content-Type: application/json" \
  -d '{}'
```

压缩短期记忆：

```bash
curl -X POST http://localhost:9900/api/memory/compress \
  -H "Content-Type: application/json" \
  -d '{}'
```

## 核心配置

主要配置文件：

```text
src/main/resources/application.yml
```

关键配置摘录：

```yaml
server:
  port: 9900

file:
  upload:
    path: ./uploads
    allowed-extensions: txt,md

milvus:
  host: 127.0.0.1
  port: 19530
  database: default

spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:mysql://localhost:3306/superbiz_agent?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8}
    username: ${SPRING_DATASOURCE_USERNAME:superbiz}
    password: ${SPRING_DATASOURCE_PASSWORD:superbiz1234}
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:}

dashscope:
  embedding:
    model: text-embedding-v4

document:
  chunk:
    max-size: 800
    overlap: 100

rag:
  top-k: 3
  model: qwen3-max

prometheus:
  base-url: http://localhost:9090
  mock-enabled: true

cls:
  mock-enabled: true

mysql:
  tool:
    enabled: false

web:
  tool:
    enabled: true

agent:
  safety:
    model-call-limit: 6
    tool-call-limit: 12
```

## Agent 工具开关

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `prometheus.mock-enabled` | `true` | 指标查询使用模拟数据 |
| `cls.mock-enabled` | `true` | 日志查询使用模拟数据 |
| `mysql.tool.enabled` | `false` | 是否启用 MySQL 查询工具 |
| `web.tool.enabled` | `true` | 是否启用外网查询工具 |
| `spring.ai.mcp.client.enabled` | `false` | 是否启用 MCP 客户端 |

开启真实工具前建议先限制数据库账号权限、查询超时时间和网络访问范围。

## 数据存储

| 数据 | 存储位置 |
| --- | --- |
| 上传文件 | `./uploads` |
| 长期记忆文件 | `./memory` |
| RAG 文档元数据 | MySQL `rag_documents` |
| AIOps 诊断任务 | MySQL `agent_diagnostic_task` |
| Agent 执行记忆 | MySQL `agent_execution_memory` |
| 文档向量 | Milvus |
| Docker 持久化数据 | `./volumes` |

## 常用开发命令

```bash
# 编译
mvn clean package

# 运行
mvn spring-boot:run

# 启动本地依赖
docker-compose -f vector-database.yml up -d

# 停止本地依赖
docker-compose -f vector-database.yml down

# 查看容器状态
docker ps
```

也可以查看 Makefile 中的辅助命令：

```bash
make help
```

注意：当前源码中的 RAG 上传接口是 `/api/rag/documents`。如果使用脚本或 Makefile 批量导入文档，请确认上传地址没有仍然指向旧接口。

## 常见问题

| 问题 | 处理方式 |
| --- | --- |
| DashScope 调用失败 | 检查 `DASHSCOPE_API_KEY` 是否在启动应用的 shell 中生效 |
| `/milvus/health` 返回 503 | 检查 Milvus、etcd、MinIO 容器是否启动完成 |
| 上传文档失败 | 确认文件类型为 `txt` 或 `md`，并检查 DashScope 与 Milvus 状态 |
| MySQL 连接失败 | 检查 `superbiz-mysql` 容器、3306 端口和账号密码 |
| AIOps 只有模拟数据 | 默认开启 Mock，关闭 `prometheus.mock-enabled` / `cls.mock-enabled` 后接入真实服务 |
| Agent 提前停止 | 调整 `agent.safety.model-call-limit` 和 `agent.safety.tool-call-limit` |

## License

MIT License. See [LICENSE](LICENSE).

