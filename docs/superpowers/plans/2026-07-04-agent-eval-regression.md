# Agent Eval Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add repeatable regression coverage for the AI Agent multi-turn intent and pending-plan patch behavior.

**Architecture:** Extend the existing JSON eval catalog with Agent plan-patch scenarios, then add lightweight backend unit tests around `PlanPatchPlanner` and `AiIntentRouter`. Tests use a fake `ChatModel` and do not call external AI providers or databases.

**Tech Stack:** Java 17, JUnit 5, Jackson, Spring Boot test dependency already present, existing `docs/ai-eval-cases.json`.

## Global Constraints

- Do not call real LLM providers in tests.
- Do not require database/Testcontainers for these tests.
- Keep cases readable for resume/demo storytelling.
- Verify with targeted Maven tests, backend compile, and frontend production build.

---

### Task 1: Agent Eval Cases

**Files:**
- Modify: `docs/ai-eval-cases.json`

**Interfaces:**
- Consumes: existing eval case array
- Produces: new `agent_plan_patch` category cases

- [x] Add case for "提醒延迟一周" preserving todo due date.
- [x] Add case for ambiguous "延迟一周" requiring clarification.
- [x] Add case for title-only modification.
- [x] Add case for "全部延后一周" as future coverage.

### Task 2: PlanPatchPlanner Unit Tests

**Files:**
- Create: `backend/src/test/java/com/noto/zhihui/agent/plan/PlanPatchPlannerTest.java`

**Interfaces:**
- Consumes: `PlanPatchPlanner.planPatch(String, PendingPlanState)`
- Produces: tests for deterministic patch, ambiguous clarification, and LLM fallback patch

- [x] Build a pending plan with one `createTodo` and one `createReminder`.
- [x] Assert "提醒延迟一周" returns `modify_pending_plan` targeting `createReminder`.
- [x] Assert "延迟一周" returns `clarify`.
- [x] Assert LLM fallback can modify title without deterministic regex.

### Task 3: AiIntentRouter Unit Tests

**Files:**
- Create: `backend/src/test/java/com/noto/zhihui/agent/AiIntentRouterTest.java`

**Interfaces:**
- Consumes: `AiIntentRouter.route(String, Long, Long, String)`
- Produces: tests for pending-plan `agent` vs `clarify`

- [x] Assert pending-plan context routes "提醒延迟一周" to `agent`.
- [x] Assert pending-plan context routes "延迟一周" to `clarify`.
- [x] Assert no pending-plan context keeps "延迟一周" out of forced agent path.

### Task 4: Verification

**Files:**
- No source changes.

- [x] Run targeted Maven tests.
- [x] Run backend compile.
- [x] Run frontend production build.
