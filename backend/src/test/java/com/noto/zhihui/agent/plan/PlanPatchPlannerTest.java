package com.noto.zhihui.agent.plan;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlanPatchPlannerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void reminderShiftShouldOnlyPatchReminderTriggerAt() {
        PlanPatchPlanner planner = new PlanPatchPlanner(new FakeChatModel("{}"), objectMapper);

        PlanPatch patch = planner.planPatch("提醒延迟一周", pendingTodoAndReminder());

        assertNotNull(patch);
        assertEquals("modify_pending_plan", patch.getType());
        assertEquals("createReminder", patch.getTargetTool());
        assertEquals("step_2", patch.getTargetStepId());
        assertEquals("2026-07-13T09:00", patch.getUpdates().get("triggerAt"));
    }

    @Test
    void ambiguousShiftShouldAskForClarification() {
        PlanPatchPlanner planner = new PlanPatchPlanner(new FakeChatModel("{}"), objectMapper);

        PlanPatch patch = planner.planPatch("延迟一周", pendingTodoAndReminder());

        assertNotNull(patch);
        assertEquals("clarify", patch.getType());
        assertEquals("你想把待办时间延迟，还是只把提醒时间延迟？", patch.getQuestion());
    }

    @Test
    void llmFallbackShouldReturnStructuredTitlePatch() {
        String response = """
                {"type":"modify_pending_plan","targetTool":"createTodo","targetStepId":"step_1","updates":{"title":"完善 Noto 项目描述"}}
                """;
        PlanPatchPlanner planner = new PlanPatchPlanner(new FakeChatModel(response), objectMapper);

        PlanPatch patch = planner.planPatch("标题改成完善 Noto 项目描述", pendingTodoAndReminder());

        assertNotNull(patch);
        assertEquals("modify_pending_plan", patch.getType());
        assertEquals("createTodo", patch.getTargetTool());
        assertEquals("step_1", patch.getTargetStepId());
        assertEquals("完善 Noto 项目描述", patch.getUpdates().get("title"));
    }

    private PendingPlanState pendingTodoAndReminder() {
        PendingPlanState state = new PendingPlanState();
        state.setPlanId("plan_1");
        state.setStatus("pending_confirm");
        state.setSteps(new ArrayList<>(List.of(
                step("step_1", "createTodo", Map.of(
                        "title", "改简历，加入 Noto 项目",
                        "dueAt", "2026-07-06T09:00"
                )),
                step("step_2", "createReminder", Map.of(
                        "message", "改简历，加入 Noto 项目",
                        "todoTitle", "改简历，加入 Noto 项目",
                        "triggerAt", "2026-07-06T09:00"
                ))
        )));
        return state;
    }

    private PendingPlanStep step(String id, String tool, Map<String, Object> args) {
        PendingPlanStep step = new PendingPlanStep();
        step.setStepId(id);
        step.setTool(tool);
        step.setArgs(new HashMap<>(args));
        step.setStatus("pending_confirm");
        return step;
    }

    private static final class FakeChatModel implements ChatModel {
        private final String response;

        private FakeChatModel(String response) {
            this.response = response;
        }

        @Override
        public String chat(String message) {
            return response;
        }
    }
}
