package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.agent.plan.PlanPatchPlanner;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void addReminderShouldAppendReminderToPendingTodo() {
        AgentPlanner.AgentPlan plan = plan("增加提醒", pendingTodoContext("投简历"));

        assertReminderAdded(plan, "投简历");
    }

    @Test
    void addReminderWithTypoShouldAppendReminderToPreviousPendingTodo() {
        AgentPlanner.AgentPlan plan = plan("增加上个代办的提醒", pendingTodoContext("明天投简历"));

        assertReminderAdded(plan, "明天投简历");
    }

    @Test
    void addReminderWithTitleShouldAppendReminderToPendingTodo() {
        AgentPlanner.AgentPlan plan = plan("新增提醒明天投简历", pendingTodoContext("明天投简历"));

        assertReminderAdded(plan, "明天投简历");
    }

    private AgentPlanner.AgentPlan plan(String instruction, String conversationBlock) {
        AgentPlanner planner = new AgentPlanner(
                new FailingChatModel(),
                objectMapper,
                new PlanPatchPlanner(new FailingChatModel(), objectMapper)
        );

        return planner.plan(instruction, "", conversationBlock);
    }

    private String pendingTodoContext(String title) {
        return """
                待确认方案：
                - stepId=step-1; tool=createTodo; title=%s; dueAt=2026-07-07T23:59:59; status=pending_confirm
                PENDING_PLAN_JSON:{"steps":[{"stepId":"step-1","tool":"createTodo","args":{"title":"%s","dueAt":"2026-07-07T23:59:59"},"status":"pending_confirm"}]}
                """.formatted(title, title);
    }

    private void assertReminderAdded(AgentPlanner.AgentPlan plan, String title) {
        assertNotNull(plan);
        assertEquals("已给待确认的待办补充提醒，请确认下方新方案。", plan.getReply());
        assertEquals(2, plan.getToolCalls().size());
        assertEquals("createTodo", plan.getToolCalls().get(0).tool());
        assertEquals(title, plan.getToolCalls().get(0).args().get("title"));
        assertEquals("createReminder", plan.getToolCalls().get(1).tool());
        assertEquals(title, plan.getToolCalls().get(1).args().get("todoTitle"));
        assertEquals(title, plan.getToolCalls().get(1).args().get("message"));
        assertEquals("2026-07-07T23:59:59", plan.getToolCalls().get(1).args().get("triggerAt"));
    }

    private static final class FailingChatModel implements ChatModel {
        @Override
        public String chat(String message) {
            throw new AssertionError("ChatModel should not be called for deterministic pending reminder patch");
        }
    }
}
