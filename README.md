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

记忆系统分为两层：

- **长期记忆**：文件存储在 `./memory`，入口索引为 `MEMORY.md`，话题文件位于 `memory/topics/`。
- **短期记忆**：Agent 每次执行后的输入、输出、状态和上下文快照写入 MySQL `agent_execution_memory`。

长期记忆不依赖前端按钮手动维护。后端会在成功对话后自动让模型判断本轮记录是否有长期保存价值；达到阈值时会批量回顾最近成功记录；短期记忆压缩前也会先抽取长期记忆，避免压缩导致细节丢失。模型通过 `hasMemory` 决定是否真正写入长期记忆。

短期记忆也会自动维护。默认同一 `sessionId` 下未压缩记录超过阈值后，保留最近记录，把更旧记录压缩成一条 `COMPRESSED` 摘要记录。前端只展示记录，不负责触发压缩。

`MEMORY.md` 和 `topics/*.md` 之间会自动保持一致：

- 创建或更新 topic 文件时，缺失索引会自动补齐。
- 删除 topic 文件时，会同步移除 `MEMORY.md` 中对应索引块。
- `MEMORY.md` 中引用不存在的 topic 会被清理。
- 重复 topic 索引会去重。
- 自动抽取写入 topic 后，会用模型返回的 description / keywords 更新索引块。
- 文件读写通过单进程锁串行化，避免自动抽取和手动编辑并发覆盖。

长期记忆文件：

```bash
curl "http://localhost:9900/api/memory/files?type=all"
```

执行记忆：

```bash
curl "http://localhost:9900/api/memory/executions?page=1&pageSize=10"
```

手动抽取和压缩接口仍保留给调试或运维补救使用，正常运行不依赖前端手动触发：

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

memory:
  base-path: ./memory
  max-index-lines: 200
  short-memory-pairs: 6
  short-compression:
    enabled: true
    threshold: 12
    keep-recent: 6
    max-records: 30
  long-extraction:
    enabled: true
    success-threshold: 5
    before-compression: true

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
| `memory.short-compression.enabled` | `true` | 是否自动压缩旧短期记忆 |
| `memory.long-extraction.enabled` | `true` | 是否自动抽取长期记忆 |

开启真实工具前建议先限制数据库账号权限、查询超时时间和网络访问范围。

## 工具系统

项目把模型可用能力分为三类：

- **Tools**：本地 Java 工具，通过 Spring AI `@Tool` 暴露，例如时间、RAG 内部文档、Prometheus 告警、日志、MySQL、外网 HTTP 查询、公共搜索、天气/地图。
- **MCP**：外部工具服务，通过 Spring AI `ToolCallbackProvider` 接入，并在创建 Agent 时注册为 MCP tool callbacks。
- **Skills**：计划作为“提示词能力包”加载，用于给模型注入某类任务的工作方法、约束和示例；Skills 不直接执行动作，动作仍由 Tools/MCP 完成。

当前工具注册集中在 `ToolSystemService`：

```text
ToolSystemService
├── listTools()                  # 扫描 @ManagedTool 本地工具并返回管理列表
├── setToolEnabled()             # 修改本地工具开关并刷新内存快照
├── buildLocalToolObjects()      # 注册已启用的本地 @Tool 对象
├── getMcpToolCallbacks()        # 注册 MCP 工具回调
├── buildToolInstructions()      # 生成工具使用说明，注入 system prompt
└── logAvailableTools()          # 输出当前可用本地工具和 MCP 工具
```

`ChatService` 创建 ReactAgent 时会同时注册本地工具和 MCP 工具：

```java
ReactAgent.builder()
    .methodTools(toolSystemService.buildLocalToolObjects())
    .tools(toolSystemService.getMcpToolCallbacks())
```

本地 Tools 由后端代码定义，前端只负责展示和开关，不允许新增任意工具。每个本地工具类通过 `@ManagedTool` 声明展示名称、风险等级和默认开关，`ToolSystemService` 会扫描这些 Spring Bean，不需要维护额外的中心工具清单。工具开关落库到 MySQL `tool_config`，应用启动时会补齐缺失的默认配置，并把已启用工具加载为内存快照；聊天线程只读快照，管理接口更新数据库后再原子刷新快照。

新增外部信息类工具时，优先复制当前轻量模式：工具类自己持有 HTTP 客户端、配置超时和返回结构化 JSON，不直接引入整套 starter。当前已内置：

| 工具 | 方法 | 说明 |
| --- | --- | --- |
| `public_search` | `searchPublicWeb` | 查询公开搜索摘要和相关链接 |
| `weather_map` | `getCurrentWeather` | 按城市或地点查询当前天气 |
| `weather_map` | `searchMapPlace` | 按地点名称查询公开地图地理编码结果 |

| 接口 | 说明 |
| --- | --- |
| `GET /api/tools` | 查询本地工具列表、可用状态、启用状态和风险等级 |
| `PUT /api/tools/{toolName}/enabled` | 修改指定本地工具开关，请求体为 `{"enabled": true}` |

本地工具适合放强约束、可审计的能力；MCP 适合接入外部系统；Skills 后续适合做 Java 调试、AIOps 诊断、RAG 维护等任务的专用提示词包。

### MCP 管理

MCP Server 配置由前端管理并落库到 MySQL `mcp_server_config`。当前支持 `SSE` 和 `STREAMABLE_HTTP` 两种远程传输，不支持前端配置 `STDIO`，避免把任意本地命令执行能力暴露给页面。需要认证的远程 MCP 可以通过 `headers_json` 配置请求头：

```json
{
  "Authorization": "Bearer your_token",
  "X-API-Key": "your_api_key"
}
```

远程 MCP 初始化慢时，可以在前端配置 `request_timeout_seconds`，允许范围为 5 到 180 秒。连接失败时后端会保存简化后的根因到 `last_error`，日志会打印脱敏后的最终 URL 方便排查。

运行时采用快照模式：

```text
mcp_server_config
    ↓
McpServerService
    ↓
McpRuntimeRegistry
    ↓
AtomicReference<McpToolSnapshot>
    ↓
ChatService / ToolSystemService 只读快照
```

管理接口更新配置后会重建 MCP 连接，并原子替换工具回调快照；对话线程不会在请求过程中查询数据库或重建连接。

应用启动时只创建/迁移 `mcp_server_config` 表，不主动连接远程 MCP Server。已启用的 MCP 会标记为 `UNKNOWN`，需要在前端点击刷新或通过管理接口变更配置后再重建运行时，避免无效远程 MCP 影响系统启动。

| 接口 | 说明 |
| --- | --- |
| `GET /api/mcp/servers` | 查询 MCP Server 配置和运行状态 |
| `POST /api/mcp/servers` | 新增 MCP Server，并刷新运行时 |
| `PUT /api/mcp/servers/{id}` | 更新 MCP Server 配置 |
| `DELETE /api/mcp/servers/{id}` | 删除 MCP Server，并刷新运行时 |
| `PUT /api/mcp/servers/{id}/enabled` | 启用或停用 MCP Server |
| `POST /api/mcp/servers/refresh` | 重建全部已启用 MCP 连接 |
| `POST /api/mcp/servers/{id}/refresh` | 刷新 MCP 运行时 |
| `GET /api/mcp/tools` | 查询当前 MCP 工具快照 |

### Skills 管理

Skills 采用文件系统存储，不落 MySQL。每个 Skill 是一个目录，必须包含 `SKILL.md`，可以附带 `references/`、`scripts/`、`assets/` 等资源。第一版安装入口只支持 ZIP URL，后端会下载、解压、校验 `SKILL.md`、防路径穿越，然后复制到本地目录。

```text
skills/
├── registry.json
└── installed/
    └── java-debug/
        ├── SKILL.md
        ├── references/
        ├── scripts/
        └── assets/
```

`registry.json` 保存快速列表和启用状态，`SKILL.md` 保存能力说明。复制整个 `skills/` 目录即可迁移已安装 Skills。

对话时后端会从 `registry.json` 中读取已启用 Skills，根据用户输入与 Skill 名称、描述、`SKILL.md` 前部内容做轻量关键词匹配，最多加载 3 个相关 `SKILL.md` 注入 system prompt。当前版本不使用向量检索，也不会执行 Skill 目录中的脚本。

| 接口 | 说明 |
| --- | --- |
| `GET /api/skills` | 查询已安装 Skills |
| `GET /api/skills/{name}` | 读取 Skill 详情和 `SKILL.md` 内容 |
| `POST /api/skills/install` | 从 ZIP URL 安装 Skill |
| `PUT /api/skills/{name}/enabled` | 启用或停用 Skill |
| `DELETE /api/skills/{name}` | 删除 Skill 目录并更新 registry |

## 数据存储

| 数据 | 存储位置 |
| --- | --- |
| 上传文件 | `./uploads` |
| 长期记忆文件 | `./memory` |
| RAG 文档元数据 | MySQL `rag_documents` |
| AIOps 诊断任务 | MySQL `agent_diagnostic_task` |
| Agent 执行记忆 | MySQL `agent_execution_memory` |
| 本地工具开关 | MySQL `tool_config` |
| MCP 服务配置 | MySQL `mcp_server_config` |
| Skills 能力包 | `./skills` |
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
