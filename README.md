# SuperBizAgent

SuperBizAgent 是一个基于 Spring Boot、Spring AI Alibaba、DashScope 和 Milvus 的企业智能代理服务。项目把通用对话、RAG 知识库检索、AIOps 诊断任务和 Agent 记忆管理放在同一个后端应用中，适合用于验证企业知识问答、运维告警分析、日志诊断和工具调用型 Agent 流程。

## 项目定位

系统主要解决两类问题：

1. **业务知识问答**
   - 上传 `txt`、`md` 文档并自动切分。
   - 调用 DashScope Embedding 模型生成向量。
   - 写入 Milvus 后通过 TopK 召回增强模型回答。
   - 支持普通 JSON 对话和 SSE 流式输出。

2. **AIOps 智能诊断**
   - 创建诊断任务并持久化到 MySQL。
   - 通过 Agent 工具查询指标、日志、内部文档、数据库和外部信息。
   - 保留执行记忆，支持后续压缩、抽取和查询。
   - 默认提供 Mock 模式，方便在没有 Prometheus / CLS 环境时本地演示。

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 对话 Agent | 基于 Spring AI Alibaba Agent Framework，支持工具调用和多轮会话 |
| RAG 知识库 | 文件上传、文档切分、向量化、Milvus 索引、TopK 召回 |
| 流式输出 | `/api/chat_stream` 使用 SSE 返回模型生成过程 |
| 诊断任务 | `/api/ai_ops/tasks` 创建 AIOps 任务，结果写入 MySQL |
| 记忆管理 | 长期记忆文件、短期执行记忆、压缩和抽取接口 |
| 本地依赖 | Docker Compose 启动 MySQL、Milvus、MinIO、etcd、Attu |
| Web 控制台 | 静态页面位于 `src/main/resources/static`，服务启动后访问根路径 |

## 技术栈

| 组件 | 版本 / 配置 |
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

## 架构概览

```text
User / Web UI / curl
        |
        v
Spring Boot Controllers
        |
        +-- ChatApplicationService
        |       +-- ChatSessionService
        |       +-- Agent tools
        |
        +-- RagDocumentService
        |       +-- DocumentChunkService
        |       +-- VectorEmbeddingService
        |       +-- VectorIndexService
        |
        +-- RagRetrievalService
        |       +-- VectorSearchService
        |
        +-- AIOpsTaskService / AiOpsService
        |       +-- QueryMetricsTools
        |       +-- QueryLogsTools
        |       +-- InternalDocsTools
        |       +-- MySqlTools
        |       +-- WebSearchTools
        |
        +-- MemoryService / AgentExecutionMemoryService
                +-- file memory
                +-- MySQL execution memory

External dependencies:
DashScope, Milvus, MySQL, optional Prometheus / CLS / MCP
```

## 目录结构

```text
.
├── aiops-docs/                 # AIOps 示例知识文档
├── docker/mysql/init/          # MySQL 初始化 SQL
├── docs/rag-api.md             # RAG API 详细说明
├── memory/                     # 本地长期记忆文件目录
├── src/main/java/org/example/
│   ├── agent/tool/             # Agent 可调用工具
│   ├── config/                 # DashScope、Milvus、上传、异步等配置
│   ├── controller/             # Chat、RAG、Memory、Milvus API
│   ├── dto/                    # 请求和响应对象
│   ├── entity/                 # MySQL 持久化实体
│   ├── mapper/                 # MyBatis Plus Mapper
│   └── service/                # 核心业务服务
├── src/main/resources/
│   ├── application.yml         # 应用配置
│   └── static/                 # Web 页面
├── uploads/                    # 上传后的 RAG 文档
├── vector-database.yml         # MySQL + Milvus 本地依赖
└── pom.xml
```

## 环境要求

- JDK 17
- Maven 3.8+
- Docker 和 Docker Compose
- DashScope API Key

设置模型 API Key：

```bash
export DASHSCOPE_API_KEY="your-dashscope-api-key"
```

如需覆盖 MySQL 连接：

```bash
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/superbiz_agent?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
export SPRING_DATASOURCE_USERNAME="superbiz"
export SPRING_DATASOURCE_PASSWORD="superbiz1234"
```

## 快速启动

### 1. 启动本地依赖

```bash
docker-compose -f vector-database.yml up -d
```

启动后默认端口：

| 服务 | 地址 |
| --- | --- |
| MySQL | `localhost:3306` |
| Milvus | `localhost:19530` |
| Milvus Attu | `http://localhost:8000` |
| MinIO Console | `http://localhost:9001` |

默认数据库账号：

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

服务默认监听：

```text
http://localhost:9900
```

浏览器访问根路径可打开内置 Web 页面：

```text
http://localhost:9900
```

### 3. 检查 Milvus 连接

```bash
curl http://localhost:9900/milvus/health
```

### 4. 上传知识库文档

```bash
curl -X POST http://localhost:9900/api/rag/documents \
  -F "file=@aiops-docs/cpu_high_usage.md"
```

批量上传示例：

```bash
for file in aiops-docs/*.md; do
  curl -X POST http://localhost:9900/api/rag/documents -F "file=@${file}"
done
```

## 常用接口

### 普通对话

```bash
curl -X POST http://localhost:9900/api/chat \
  -H "Content-Type: application/json" \
  -d '{"Id":"demo-session","Question":"CPU 使用率过高应该怎么排查？"}'
```

请求字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `Id` | 否 | 会话 ID，不传时由服务逻辑处理 |
| `Question` | 是 | 用户问题 |

### 流式对话

```bash
curl -N -X POST http://localhost:9900/api/chat_stream \
  -H "Content-Type: application/json" \
  -d '{"Id":"demo-session","Question":"总结一下磁盘空间告警处理流程"}'
```

返回类型为：

```text
text/event-stream;charset=UTF-8
```

### 会话管理

```bash
curl http://localhost:9900/api/chat/session/demo-session
```

```bash
curl -X POST http://localhost:9900/api/chat/clear \
  -H "Content-Type: application/json" \
  -d '{"Id":"demo-session"}'
```

### RAG 文档列表

```bash
curl "http://localhost:9900/api/rag/documents?page=1&pageSize=20&keyword=cpu"
```

### RAG 召回测试

该接口只返回向量召回片段，不调用大模型生成答案。

```bash
curl -X POST http://localhost:9900/api/rag/retrieve \
  -H "Content-Type: application/json" \
  -d '{"text":"CPU 使用率过高怎么排查","topK":5}'
```

### 创建 AIOps 诊断任务

```bash
curl -X POST http://localhost:9900/api/ai_ops/tasks \
  -H "Content-Type: application/json" \
  -d '{"alertName":"CPUHighUsage","severity":"critical","description":"prod-app-01 CPU 使用率持续高于 90%"}'
```

查询任务：

```bash
curl http://localhost:9900/api/ai_ops/tasks/{taskId}
```

### 记忆管理

```bash
# 长期记忆文件列表
curl "http://localhost:9900/api/memory/files?type=all"

# 短期执行记忆列表
curl "http://localhost:9900/api/memory/executions?page=1&pageSize=10"

# 抽取长期记忆
curl -X POST http://localhost:9900/api/memory/extract \
  -H "Content-Type: application/json" \
  -d '{}'

# 压缩短期记忆
curl -X POST http://localhost:9900/api/memory/compress \
  -H "Content-Type: application/json" \
  -d '{}'
```

更多 RAG 接口细节见 [docs/rag-api.md](docs/rag-api.md)。

## 关键配置

主要配置位于 `src/main/resources/application.yml`。

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

agent:
  safety:
    model-call-limit: 6
    tool-call-limit: 12
```

## 工具开关

| 配置 | 默认值 | 说明 |
| --- | --- | --- |
| `prometheus.mock-enabled` | `true` | 使用模拟指标数据 |
| `cls.mock-enabled` | `true` | 使用模拟日志数据 |
| `mysql.tool.enabled` | `false` | 是否允许 Agent 调用 MySQL 查询工具 |
| `web.tool.enabled` | `true` | 是否允许 Agent 调用外网查询工具 |
| `spring.ai.mcp.client.enabled` | `false` | 是否启用 MCP 客户端 |

生产或准生产环境建议先明确工具权限、查询范围和网络访问策略，再打开数据库、外网或 MCP 工具。

## 数据存储

| 数据 | 存储位置 |
| --- | --- |
| 上传文件 | `./uploads` |
| 长期记忆文件 | `./memory` |
| RAG 文档元数据 | MySQL `rag_documents` |
| AIOps 诊断任务 | MySQL `agent_diagnostic_task` |
| Agent 执行记忆 | MySQL `agent_execution_memory` |
| 文档向量 | Milvus |
| Docker 数据卷 | `./volumes` |

## 开发命令

```bash
# 编译
mvn clean package

# 启动后端
mvn spring-boot:run

# 启动依赖
docker-compose -f vector-database.yml up -d

# 停止依赖
docker-compose -f vector-database.yml down

# 查看容器
docker ps
```

仓库也提供 `Makefile`，可查看所有命令：

```bash
make help
```

注意：当前 `Makefile` 的 `upload` 目标仍使用旧上传路径 `/api/upload`，源码中的真实 RAG 上传接口是 `/api/rag/documents`。批量导入文档时建议使用上文的 `curl` 循环，或先更新 `Makefile` 中的 `UPLOAD_API`。

## 排障

| 现象 | 排查方向 |
| --- | --- |
| `DASHSCOPE_API_KEY` 为空 | 确认环境变量已在启动应用的同一个 shell 中导出 |
| `/milvus/health` 返回 503 | 检查 `milvus-standalone`、`milvus-etcd`、`milvus-minio` 是否已启动 |
| 上传文档失败 | 确认文件扩展名为 `txt` 或 `md`，并检查 Milvus 和 DashScope API Key |
| MySQL 连接失败 | 检查 `superbiz-mysql` 容器、端口 3306、账号密码和初始化 SQL |
| AIOps 查询不到真实指标 | 默认开启 Mock；接入真实 Prometheus 需关闭 `prometheus.mock-enabled` 并配置 `base-url` |
| Agent 工具调用过早停止 | 检查 `agent.safety.model-call-limit` 和 `agent.safety.tool-call-limit` |

## 许可证

本项目使用 MIT License，见 [LICENSE](LICENSE)。
