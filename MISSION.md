# Mission: Noto AI Project Interview Fluency

## Why
You want to understand the AI development work in this project well enough to explain it confidently on your resume and handle technical follow-up questions in an interview.

## Success looks like
- Explain the AI architecture as a product flow, not just as a list of features.
- Trace one RAG question from frontend request to retrieval, prompt construction, model call, SSE response, references, and audit logging.
- Explain why the Agent uses preview/confirmation before writes, and how that protects user data.
- Answer common follow-ups about hallucination, fallback behavior, evaluation, performance, and deployment.
- Understand AI terms from a Java backend developer's point of view: LLM, prompt, token, embedding, vector search, RAG, SSE, Agent, tool call, and audit log.
- Build a small AI module independently in this Spring Boot project: controller -> service -> prompt -> model call -> parsing/fallback -> audit.

## Constraints
- Lessons should stay tied to the real code in this repository.
- Prefer short, interview-oriented lessons with retrieval practice.
- Avoid broad AI theory unless it directly helps explain this project.
- Assume Java backend experience, but near-zero prior AI development experience.

## Out of scope
- Training or fine-tuning models.
- Deep mathematical derivations of embeddings or HNSW.
- Rebuilding the AI subsystem from scratch.
