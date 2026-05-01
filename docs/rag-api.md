# RAG API 文档

基础地址：

```text
http://localhost:9900
```

## 1. 上传 RAG 文档

```http
POST /api/rag/documents
Content-Type: multipart/form-data
```

用途：上传知识库文档，保存到 `./uploads`，写入 `rag_documents` 元数据表，并同步建立向量索引。

表单字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `file` | file | 是 | 当前配置仅支持 `txt`、`md` |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "documentId": "uuid",
    "fileName": "cpu_high_usage.md",
    "filePath": "uploads/cpu_high_usage.md",
    "fileSize": 12345,
    "extension": "md",
    "status": "INDEXED",
    "updatedAt": 1777560000000
  }
}
```

说明：

- 同名文件会覆盖。
- 上传成功后会立即调用向量索引，索引状态会写入 `rag_documents.status`。
- 索引失败时接口返回 `500`，同时记录 `FAILED` 状态和失败原因。

## 2. 查询 RAG 文档列表

```http
GET /api/rag/documents?page=1&pageSize=20&keyword=cpu
```

用途：从 MySQL `rag_documents` 表分页查询当前知识库文档列表。

查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `page` | number | 否 | `1` | 页码 |
| `pageSize` | number | 否 | `20` | 每页条数，最大 100 |
| `keyword` | string | 否 | 空 | 按文件名过滤 |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "items": [
      {
        "documentId": "uuid",
        "fileName": "cpu_high_usage.md",
        "filePath": "uploads/cpu_high_usage.md",
        "fileSize": 12345,
        "extension": "md",
        "status": "INDEXED",
        "updatedAt": 1777560000000
      }
    ],
    "total": 1,
    "page": 1,
    "pageSize": 20
  }
}
```

## 3. RAG 召回

```http
POST /api/rag/retrieve
Content-Type: application/json
```

用途：对知识库进行向量检索，只返回 TopK 召回片段，不调用大模型生成答案。

请求体：

```json
{
  "text": "CPU 使用率过高怎么排查",
  "topK": 5
}
```

字段说明：

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `text` | string | 是 | - | 待召回文本，可为问题、日志片段、告警描述或事故摘要。兼容旧字段 `query`。 |
| `topK` | number | 否 | `5` | 返回条数，最大 20 |
| `minScore` | number | 否 | 空 | 当前为 L2 distance 过滤阈值，distance 小于等于该值才返回。 |

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "text": "CPU 使用率过高怎么排查",
    "topK": 5,
    "scoreType": "L2_DISTANCE",
    "higherScoreBetter": false,
    "results": [
      {
        "rank": 1,
        "chunkId": "chunk-id",
        "content": "命中的文档片段",
        "distance": 0.123,
        "scoreType": "L2_DISTANCE",
        "metadata": "{\"_source\":\"uploads/cpu_high_usage.md\",\"_file_name\":\"cpu_high_usage.md\"}",
        "source": "uploads/cpu_high_usage.md",
        "fileName": "cpu_high_usage.md"
      }
    ]
  }
}
```

说明：

- 当前 Milvus 检索使用 L2 distance，`distance` 越小越相似。
