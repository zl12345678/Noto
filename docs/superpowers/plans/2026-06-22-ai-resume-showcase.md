# AI Resume Showcase Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Noto's existing AI features into a resume-ready, demoable AI engineering showcase.

**Architecture:** The first release uses existing AI/RAG/Agent APIs and adds a desktop web showcase page plus documentation assets. Later backend observability work can expose `audit_log` summaries through a dedicated endpoint without blocking the showcase.

**Tech Stack:** Vue 3, TypeScript, Ant Design Vue, Spring Boot 3, LangChain4j, DashScope, PostgreSQL pgvector, Docker.

## Global Constraints

- Do not add a new AI provider; use the existing DashScope/LangChain4j integration.
- Do not expose secrets, prompts with private user content, or raw API keys in the showcase.
- Keep all AI write operations user-confirmed; the showcase may explain the confirm chain but must not bypass it.
- Prefer existing APIs: `/ai/status`, `/ai/rag/status`, `/ai/agent/tasks`, `/ai/digest/today`, `/ai/weekly-retro/current`.
- Keep frontend additions isolated under `frontend/src/views/ai-showcase/`.
- Documentation assets must live under `docs/` and be usable during interviews without running the app.

---

## File Structure

- Create `frontend/src/views/ai-showcase/AiShowcaseView.vue`: resume-facing AI capability dashboard using existing APIs.
- Modify `frontend/src/router/index.ts`: add `/ai-showcase` route.
- Modify `frontend/src/layouts/AppLayout.vue`: add sidebar entry and selected-key handling.
- Create `docs/AI_RESUME_DEMO.md`: 3-minute interview demo script.
- Create `docs/AI_EVAL.md`: RAG evaluation protocol.
- Create `docs/ai-eval-cases.json`: seed evaluation cases for RAG and action intelligence.
- Modify `README.md`: add a concise resume-oriented AI highlight section linking to the new docs.

---

### Task 1: AI Showcase Page

**Files:**
- Create: `frontend/src/views/ai-showcase/AiShowcaseView.vue`
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/layouts/AppLayout.vue`

**Interfaces:**
- Consumes: `getAiStatus(): Promise<AiStatus>` from `frontend/src/api/ai.ts`
- Consumes: `getNoteRagStatus(workspaceId?: string): Promise<NoteRagIndexStatus>` from `frontend/src/api/ai.ts`
- Consumes: `getTodayAiDigest(): Promise<AiDigest | null>` from `frontend/src/api/ai.ts`
- Consumes: `getCurrentWeeklyRetro(): Promise<AiWeeklyRetro | null>` from `frontend/src/api/ai.ts`
- Consumes: `listAgentTasks({ page, size }): Promise<AiAgentTaskPage>` from `frontend/src/api/aiAgent.ts`
- Produces: route name `ai-showcase` at `/ai-showcase`

- [x] **Step 1: Create the showcase view**

Create `frontend/src/views/ai-showcase/AiShowcaseView.vue` with:

```vue
<template>
  <div class="showcase-page">
    <section class="showcase-hero">
      <p class="eyebrow">AI engineering showcase</p>
      <h1>Noto turns private knowledge into actionable work.</h1>
      <p class="hero-copy">
        A resume-ready view of RAG retrieval, confirmed agent workflows, AI task automation, and production guardrails.
      </p>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <span class="metric-label">AI provider</span>
        <strong>{{ aiStatus?.provider || 'unknown' }}</strong>
        <small>{{ aiStatus?.model || 'model unavailable' }}</small>
      </article>
      <article class="metric-card">
        <span class="metric-label">RAG chunks</span>
        <strong>{{ ragStatus?.indexedChunks ?? '-' }}</strong>
        <small>{{ ragStatus?.embeddingModel || 'embedding model unavailable' }}</small>
      </article>
      <article class="metric-card">
        <span class="metric-label">Indexed notes</span>
        <strong>{{ ragStatus?.indexedNotes ?? '-' }}</strong>
        <small>{{ ragStatus?.ragAvailable ? 'pgvector/search available' : 'RAG unavailable' }}</small>
      </article>
      <article class="metric-card">
        <span class="metric-label">Recent agent runs</span>
        <strong>{{ agentTasks.length }}</strong>
        <small>latest confirmed workflows</small>
      </article>
    </section>

    <section class="showcase-section">
      <div class="section-copy">
        <p class="eyebrow">RAG retrieval</p>
        <h2>Private knowledge answers with citations.</h2>
        <p>
          Notes are chunked, embedded, indexed, and retrieved before answer generation. The UI surfaces
          cited documents so interviewers can inspect grounding rather than trusting a black box.
        </p>
      </div>
      <div class="capability-list">
        <span>Document chunking</span>
        <span>DashScope embeddings</span>
        <span>pgvector HNSW</span>
        <span>Keyword fallback</span>
        <span>Reference jump</span>
      </div>
    </section>

    <section class="showcase-section">
      <div class="section-copy">
        <p class="eyebrow">Confirmed agent</p>
        <h2>Natural language becomes auditable tool calls.</h2>
        <p>
          Agent workflows split requests into steps such as searching notes, summarizing, extracting todos,
          creating reminders, and waiting for confirmation before writes.
        </p>
      </div>
      <div class="agent-list">
        <article v-for="task in agentTasks" :key="task.id" class="agent-card">
          <div class="agent-card-head">
            <strong>{{ task.instruction || 'Agent workflow' }}</strong>
            <span>{{ taskStatusLabel(task.status) }}</span>
          </div>
          <ol>
            <li v-for="step in task.steps || []" :key="step.id">
              {{ toolLabel(step.tool) }} · {{ step.status }}{{ step.requiresConfirm ? ' · requires confirmation' : '' }}
            </li>
          </ol>
        </article>
        <div v-if="!agentTasks.length" class="empty-panel">
          Run an agent task from AI Assistant to populate this timeline.
        </div>
      </div>
    </section>

    <section class="showcase-section">
      <div class="section-copy">
        <p class="eyebrow">Workflow automation</p>
        <h2>AI output lands back in the product loop.</h2>
        <p>
          Digest, daily review, weekly retro, todo extraction, and completion retros connect AI output
          to notes, todos, reminders, and review artifacts.
        </p>
      </div>
      <div class="automation-grid">
        <article>
          <span>Today digest</span>
          <strong>{{ digest?.generatedAt ? 'generated' : 'not generated today' }}</strong>
        </article>
        <article>
          <span>Weekly retro</span>
          <strong>{{ weeklyRetro?.noteTitle || 'not generated this week' }}</strong>
        </article>
      </div>
    </section>
  </div>
</template>
```

- [x] **Step 2: Add the route**

Modify `frontend/src/router/index.ts`:

```ts
const AiShowcaseView = () => import('../views/ai-showcase/AiShowcaseView.vue');
```

Add child route:

```ts
{
  path: 'ai-showcase',
  name: 'ai-showcase',
  component: AiShowcaseView,
},
```

- [x] **Step 3: Add sidebar navigation**

Modify `frontend/src/layouts/AppLayout.vue` inside the "概览" menu:

```vue
<a-menu-item key="ai-showcase">
  <span class="entry-item">
    <span class="entry-dot purple"></span>
    <span>AI 展示</span>
  </span>
</a-menu-item>
```

Update selected keys:

```ts
if (route.name === 'ai-showcase') return ['ai-showcase'];
```

Update click handling:

```ts
if (key === 'ai-showcase') {
  activateModuleTab('ai-showcase', () => router.push('/ai-showcase'));
  return;
}
```

- [x] **Step 4: Run frontend build**

Run: `cd frontend && npm run build`

Expected: Type check and Vite build pass.

---

### Task 2: RAG Evaluation Assets

**Files:**
- Create: `docs/AI_EVAL.md`
- Create: `docs/ai-eval-cases.json`

**Interfaces:**
- Produces: repeatable interview artifact for explaining RAG evaluation.

- [x] **Step 1: Create eval cases**

Create `docs/ai-eval-cases.json`:

```json
[
  {
    "id": "rag-project-context-001",
    "category": "knowledge_recall",
    "question": "这个项目的 AI 能力核心闭环是什么？",
    "expectedAnswerPoints": ["知识输入", "RAG 问答", "提取待办", "行动看板", "提醒或复盘"],
    "expectedEvidence": ["README.md", "docs/WEB_DESKTOP_POLISH.md"],
    "risk": "回答只列 AI 功能，而没有解释知识到行动的闭环"
  }
]
```

- [x] **Step 2: Create evaluation protocol**

Create `docs/AI_EVAL.md` with sections:

```markdown
# Noto AI Evaluation Protocol

## Goal

Evaluate whether Noto's RAG and action intelligence are grounded, useful, and demo-ready.

## Metrics

| Metric | Definition | Target |
|--------|------------|--------|
| Citation hit rate | Answer references at least one expected source | >= 80% |
| Key point coverage | Answer includes expected answer points | >= 75% |
| Actionability | Answer produces a concrete note/todo/reminder when requested | >= 80% |
| Hallucination risk | Answer invents unsupported facts | 0 critical cases |
```

---

### Task 3: Interview Demo Script

**Files:**
- Create: `docs/AI_RESUME_DEMO.md`
- Modify: `README.md`

**Interfaces:**
- Produces: one interview-ready demo flow and resume bullet list.

- [x] **Step 1: Create the demo script**

Create `docs/AI_RESUME_DEMO.md`:

```markdown
# Noto AI Resume Demo

## Positioning

Noto is not a generic chatbot. It is an AI knowledge workbench that turns private notes into actionable tasks, reminders, and retrospectives.

## 3-minute demo

1. Open AI Showcase.
2. Show RAG status and indexed chunks.
3. Ask a knowledge-base question and inspect citations.
4. Ask the agent to extract todos from a meeting note.
5. Confirm the write operation.
6. Show the todo in the action board.
7. Complete the todo and append an AI retro back to the note.
```

- [x] **Step 2: Add README links**

Add a `Resume-focused AI highlights` section to `README.md` linking the showcase and docs.

---

### Task 4: Agent Trace and Observability

**Files:**
- Create: `backend/src/main/java/com/noto/zhihui/vo/ai/AiObservabilitySummaryVO.java`
- Modify: `backend/src/main/java/com/noto/zhihui/controller/ai/AiController.java`
- Modify: `backend/src/main/java/com/noto/zhihui/service/AuditLogService.java`
- Modify: `backend/src/main/java/com/noto/zhihui/service/impl/AuditLogServiceImpl.java`
- Modify: `frontend/src/api/ai.ts`
- Modify: `frontend/src/views/ai-showcase/AiShowcaseView.vue`

**Interfaces:**
- Produces: `GET /api/v1/ai/observability/summary`
- Produces: `getAiObservabilitySummary(): Promise<AiObservabilitySummary>` in `frontend/src/api/ai.ts`

- [x] **Step 1: Implement backend endpoint contract**

Implemented response contract:

```ts
interface AiObservabilitySummary {
  totalCalls: number;
  successRate: number;
  averageLatencyMs: number | null;
  recentActions: Array<{
    actionType: string;
    resourceType?: string | null;
    success: boolean;
    latencyMs?: number;
    createdAt: string;
  }>;
}
```

- [x] **Step 2: Add the observability panel to the showcase page**

The showcase page now displays total AI calls, sample success rate, average latency, and recent audited AI actions.

- [x] **Step 3: Run backend and frontend verification**

Run:

```bash
cd backend && mvnw.cmd -q -DskipTests compile
cd frontend && npm run build:prod
```

Expected: backend compile passes; Vite production build emits `AiShowcaseView` assets.

---

## Self-Review

- Spec coverage: covers AI showcase page, RAG eval assets, Agent trace visualization, AI observability follow-up, and demo/resume packaging.
- Placeholder scan: no task contains `TBD` or unbounded "do later" implementation steps; Task 4 explicitly scopes a future endpoint contract.
- Type consistency: route name `ai-showcase`, API names, and agent task types match existing frontend API modules.
