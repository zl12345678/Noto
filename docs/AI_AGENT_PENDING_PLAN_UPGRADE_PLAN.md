# AI Agent Pending Plan Upgrade Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or equivalent task-by-task execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade the AI assistant from keyword patching toward structured pending-plan understanding, so follow-up messages such as "提醒延迟一周" modify the previous pending reminder safely.

**Architecture:** Keep the existing AgentPlanner and tool executor flow. Add a machine-readable pending-plan context alongside the current text summary, make intent routing recognize pending-plan edits by semantic cues, and make AgentPlanner apply deterministic safe patches for clear relative edits before falling back to the LLM.

**Tech Stack:** Vue 3 + TypeScript frontend, Spring Boot Java backend, LangChain4j ChatModel, existing AiAgentStepVO actionPayload.

## Global Constraints

- Do not add new database tables for this iteration.
- Do not execute write operations before user confirmation.
- Prefer structured pending-plan data over one-off keyword rules.
- Keep existing chat/agent UI behavior compatible.
- Verify with frontend production build and backend compile.

---

### Task 1: Structured Pending Plan Context

**Files:**
- Modify: `frontend/src/utils/aiContext.ts`
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AgentReplyComposer.java`
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AgentPlanner.java`

**Interfaces:**
- Consumes: `AiAgentStep.actionPayload` and `AiAgentStepVO.actionPayload`
- Produces: a `PENDING_PLAN_JSON:` context block containing `steps[{stepId, tool, args, status}]`

- [x] Add `PENDING_PLAN_JSON:` to frontend recent context after the readable `待确认方案`.
- [x] Add `PENDING_PLAN_JSON:` to persisted assistant replies so backend session context also carries structure.
- [x] Update planner pending-plan reader to prefer JSON blocks and fall back to line parsing.
- [x] Verify no UI rendering depends on the JSON block being hidden, because assistant reply may include it.

### Task 2: Semantic Pending Edit Routing

**Files:**
- Modify: `frontend/src/utils/aiIntent.ts`
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AiIntentRouter.java`

**Interfaces:**
- Consumes: recent context containing `待确认方案` or `PENDING_PLAN_JSON`
- Produces: route intent `agent` for clear pending-plan edits, `clarify` for ambiguous relative edits

- [x] Route "提醒延迟一周" and "待办推迟三天" to agent when a pending plan exists.
- [x] Route "延迟一周" to clarify when both todo and reminder are pending and the target is omitted.
- [x] Preserve existing "好/可以" continuation behavior.

### Task 3: Safe Relative Patch Planner

**Files:**
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AgentPlanner.java`

**Interfaces:**
- Consumes: `PendingPlanItem(tool, args)` parsed from structured context
- Produces: a new `AgentPlan` containing complete regenerated `createTodo` and `createReminder` calls

- [x] For "提醒延迟一周", shift only `createReminder.triggerAt` by +7 days.
- [x] For "提醒提前三天", shift only `createReminder.triggerAt` by -3 days.
- [x] For "待办延迟一周", shift only `createTodo.dueAt` by +7 days.
- [x] Keep unmodified pending steps in the regenerated plan.
- [x] Return null for ambiguous edits so routing can clarify or LLM can decide.

### Task 4: Verification

**Files:**
- No source changes required.

- [x] Run `cd frontend; npm run build:prod`.
- [x] Run `cd backend; $env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\mvnw.cmd -q -DskipTests compile`.
- [x] Inspect diffs for unrelated changes.
