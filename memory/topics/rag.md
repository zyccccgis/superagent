# RAG Memory

- RAG document upload uses `POST /api/rag/documents`.
- RAG document metadata is stored in MySQL table `rag_documents`.
- RAG chunks and vectors are stored in Milvus collection `biz`.
- RAG retrieval uses `POST /api/rag/retrieve` and returns TopK chunks with L2 distance.
