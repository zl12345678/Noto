package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.vo.ai.AiAgentStepVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentReplyComposerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pendingPlanDetailsShouldStayOutOfAssistantReply() {
        AiAgentStepVO todo = pendingStep("step-1", "createTodo", Map.of(
                "title", "明天投简历",
                "dueAt", "2026-07-07T18:00"
        ));
        AiAgentStepVO reminder = pendingStep("step-2", "createReminder", Map.of(
                "todoTitle", "明天投简历",
                "message", "明天投简历",
                "triggerAt", "2026-07-14T18:00"
        ));

        String reply = AgentReplyComposer.compose(
                "已根据你的补充修改待确认方案，请确认下方新方案。",
                List.of(todo, reminder),
                objectMapper
        );

        assertTrue(reply.contains("已根据你的补充修改待确认方案"));
        assertFalse(reply.contains("待确认方案："));
        assertFalse(reply.contains("stepId="));
        assertFalse(reply.contains("PENDING_PLAN_JSON:"));
    }

    private AiAgentStepVO pendingStep(String id, String tool, Map<String, Object> payload) {
        AiAgentStepVO step = new AiAgentStepVO();
        step.setId(id);
        step.setTool(tool);
        step.setStatus("pending_confirm");
        step.setRequiresConfirm(true);
        step.setActionPayload(payload);
        return step;
    }
}
