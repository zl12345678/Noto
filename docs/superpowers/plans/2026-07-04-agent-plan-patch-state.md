# Agent Plan Patch State Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a practical conversation-state + Pending Plan JSON + Plan Patch loop so the AI assistant can revise pending todo/reminder plans through natural follow-up instructions.

**Architecture:** Keep the existing Agent task and confirmation pipeline. Store structured pending-plan state inside the existing task payload/VO, add a patch planner that asks the LLM for structured patches when deterministic patching does not apply, then compile the patched plan back into normal Agent tool calls and confirmation steps.

**Tech Stack:** Java 17, Spring Boot, Jackson, LangChain4j `ChatModel`, Vue 3 + TypeScript, existing `AiAgentTaskVO` and `AiAgentStepVO`.

## Global Constraints

- Do not add database tables in this iteration.
- Do not execute write tools before user confirmation.
- Preserve existing confirmation card UI and confirm endpoint.
- Prefer structured state and plan patches over one-off regex additions.
- Keep backend compile and frontend production build passing.

---

### Task 1: Pending Plan State Model

**Files:**
- Create: `backend/src/main/java/com/noto/zhihui/agent/plan/PendingPlanState.java`
- Create: `backend/src/main/java/com/noto/zhihui/agent/plan/PendingPlanStep.java`
- Create: `backend/src/main/java/com/noto/zhihui/agent/plan/ConversationAgentState.java`
- Modify: `backend/src/main/java/com/noto/zhihui/vo/ai/AiAgentTaskVO.java`
- Modify: `frontend/src/api/aiAgent.ts`

**Interfaces:**
- Consumes: existing `List<AiAgentStepVO> steps`
- Produces: `PendingPlanState.fromSteps(String planId, List<AiAgentStepVO> steps)` and `AiAgentTaskVO.pendingPlan`

- [x] Add state model classes with fields `planId`, `status`, `steps`, `stepId`, `tool`, `args`.
- [x] Add `PendingPlanState.fromSteps` to collect only `pending_confirm` steps with action payload.
- [x] Add `pendingPlan` and `agentState` to `AiAgentTaskVO`.
- [x] Add matching frontend TypeScript interfaces.

### Task 2: Payload Persistence And Context

**Files:**
- Modify: `backend/src/main/java/com/noto/zhihui/service/impl/AiAgentServiceImpl.java`
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AgentReplyComposer.java`
- Modify: `frontend/src/utils/aiContext.ts`

**Interfaces:**
- Consumes: `PendingPlanState`
- Produces: task payload fields `pendingPlan` and `agentState`

- [x] Store pending plan and conversation state in `AgentTaskPayload`.
- [x] Include pending plan in `AiAgentTaskVO`.
- [x] Use `pendingPlan` from the VO in frontend context before rebuilding it from steps.
- [x] Keep `PENDING_PLAN_JSON` hidden from user-visible markdown.

### Task 3: Plan Patch Model And Planner

**Files:**
- Create: `backend/src/main/java/com/noto/zhihui/agent/plan/PlanPatch.java`
- Create: `backend/src/main/java/com/noto/zhihui/agent/plan/PlanPatchPlanner.java`
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AgentPlanner.java`

**Interfaces:**
- Consumes: instruction + `PendingPlanState`
- Produces: `PlanPatch` with `type`, `target`, `updates`, `question`, and compiled `AgentPlan`

- [x] Implement deterministic patches for clear reminder/todo relative shifts.
- [x] Add LLM fallback that outputs JSON patch for `modify_pending_plan` or `clarify`.
- [x] Apply patch to a copy of pending plan steps.
- [x] Compile patched pending plan steps back to `AgentToolCall` list.
- [x] Return a clarification plan with no tool calls when the patch asks for clarification.

### Task 4: Route And Execution Integration

**Files:**
- Modify: `backend/src/main/java/com/noto/zhihui/agent/AiIntentRouter.java`
- Modify: `frontend/src/utils/aiIntent.ts`
- Modify: `backend/src/main/java/com/noto/zhihui/service/impl/AiAgentServiceImpl.java`

**Interfaces:**
- Consumes: `PENDING_PLAN_JSON` and `pendingPlan`
- Produces: `agent` routing for plan-patchable edits, `clarify` for ambiguous edits

- [x] Keep targeted pending-plan edits routed to agent.
- [x] Keep ambiguous edits routed to clarify before execution.
- [x] Ensure zero-tool clarification tasks persist as successful assistant replies.

### Task 5: Verification

**Files:**
- No source files.

- [x] Run `cd frontend; npm run build:prod`.
- [x] Run `cd backend; $env:JAVA_HOME='C:\Program Files\Java\jdk-17'; .\mvnw.cmd -q -DskipTests compile`.
- [x] Check git diff for unrelated files.
