package com.noto.zhihui.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.noto.zhihui.config.NotoAiProperties;
import com.noto.zhihui.vo.ai.AiRouteVO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiIntentRouterTest {

    @Test
    void targetedPendingPlanEditShouldRouteToAgent() {
        AiIntentRouter router = router();

        AiRouteVO route = router.route("提醒延迟一周", null, null, pendingPlanContext());

        assertEquals("agent", route.getIntent());
        assertEquals("rule", route.getSource());
    }

    @Test
    void ambiguousPendingPlanEditShouldRouteToClarify() {
        AiIntentRouter router = router();

        AiRouteVO route = router.route("延迟一周", null, null, pendingPlanContext());

        assertEquals("clarify", route.getIntent());
        assertEquals("rule", route.getSource());
    }

    @Test
    void relativeShiftWithoutPendingPlanShouldNotForceAgent() {
        AiIntentRouter router = router();

        AiRouteVO route = router.route("延迟一周", null, null, "");

        assertEquals("chat", route.getIntent());
        assertEquals("rule", route.getSource());
    }

    private AiIntentRouter router() {
        NotoAiProperties properties = new NotoAiProperties();
        properties.setEnabled(false);
        return new AiIntentRouter(new ObjectMapper(), properties, null, null);
    }

    private String pendingPlanContext() {
        return """
                助手：已生成待确认方案。
                待确认方案：
                - stepId=step_1; tool=createTodo; title=改简历，加入 Noto 项目; dueAt=2026-07-06T09:00; status=pending_confirm
                - stepId=step_2; tool=createReminder; todoTitle=改简历，加入 Noto 项目; message=改简历，加入 Noto 项目; triggerAt=2026-07-06T09:00; status=pending_confirm
                PENDING_PLAN_JSON:{"planId":"plan_1","status":"pending_confirm","steps":[{"stepId":"step_1","tool":"createTodo","args":{"title":"改简历，加入 Noto 项目","dueAt":"2026-07-06T09:00"},"status":"pending_confirm"},{"stepId":"step_2","tool":"createReminder","args":{"message":"改简历，加入 Noto 项目","todoTitle":"改简历，加入 Noto 项目","triggerAt":"2026-07-06T09:00"},"status":"pending_confirm"}]}
                """;
    }
}
