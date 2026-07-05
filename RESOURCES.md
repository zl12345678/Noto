# Noto AI Interview Resources

## Knowledge

- [Project: AI showcase and evaluation](docs/AI_SHOWCASE.md)
  The repository's own interview-facing AI narrative. Use for demo flow, resume bullets, and evaluation criteria.
- [Project: README AI section](README.md)
  The canonical project overview. Use for feature scope, API list, roadmap status, and deployment notes.
- [Code: `AiController`](backend/src/main/java/com/noto/zhihui/controller/ai/AiController.java)
  API boundary for AI status, ask, SSE streaming, note transforms, todos, digest, and agent routing.
- [Code: `AiServiceImpl`](backend/src/main/java/com/noto/zhihui/service/impl/AiServiceImpl.java)
  Main AI service implementation. Use for prompts, ask flow, stream handling, parsing, audit logging, and note/todo AI features.
- [Code: `NoteRetrievalService`](backend/src/main/java/com/noto/zhihui/service/NoteRetrievalService.java)
  Retrieval orchestration. Use for vector-first retrieval, keyword fallback, catalog questions, references, and knowledge gaps.
- [Code: `NoteRagIndexServiceImpl`](backend/src/main/java/com/noto/zhihui/service/impl/NoteRagIndexServiceImpl.java)
  RAG indexing path. Use for chunking, embedding generation, persistence, and manual/background reindexing.
- [Code: `NoteRagSearchServiceImpl`](backend/src/main/java/com/noto/zhihui/service/impl/NoteRagSearchServiceImpl.java)
  RAG search path. Use for query embedding, pgvector search, in-memory fallback, similarity thresholding, and keyword boost.
- [Code: `PgVectorSupport`](backend/src/main/java/com/noto/zhihui/support/PgVectorSupport.java)
  pgvector integration. Use for schema initialization, HNSW index creation, vector serialization, and fallback explanation.
- [LangChain4j DashScope integration](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/language-models/dashscope.md)
  Official integration documentation for Qwen chat and streaming model classes used by this project.
- [LangChain4j DashScope embedding integration](https://github.com/langchain4j/langchain4j/blob/main/docs/docs/integrations/embedding-models/dashscope.md)
  Official embedding integration background for the DashScope embedding model used in the RAG index.
- [Alibaba Cloud Model Studio text embeddings](https://www.alibabacloud.com/help/en/model-studio/text-embedding-synchronous-api)
  Official model documentation for text embeddings, vector dimensions, and dense/sparse output concepts.
- [pgvector README](https://github.com/pgvector/pgvector/blob/master/README.md)
  Official pgvector reference for vector search and HNSW/IVFFlat indexing trade-offs.
- [Spring `SseEmitter` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html)
  Official Spring API reference for server-sent event streaming.

## Wisdom (Communities)

- [LangChain4j GitHub Discussions](https://github.com/langchain4j/langchain4j/discussions)
  Good place to validate framework-specific patterns and edge cases around LangChain4j integrations.
- [pgvector GitHub Issues](https://github.com/pgvector/pgvector/issues)
  Useful for production questions about pgvector indexing, dimensions, and database-level vector behavior.
- [Alibaba Cloud Model Studio documentation center](https://www.alibabacloud.com/help/en/model-studio/)
  Official source for DashScope behavior changes, model parameters, and embedding model updates.
