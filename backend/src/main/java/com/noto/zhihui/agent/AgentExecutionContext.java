package com.noto.zhihui.agent;

import com.noto.zhihui.vo.ai.AiAgentStepVO;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public final class AgentExecutionContext {

    private static final ThreadLocal<AgentExecutionContext> HOLDER = new ThreadLocal<>();

    private final Long userId;
    private final Long workspaceId;
    private final boolean dryRun;
    private final List<AiAgentStepVO> steps = new ArrayList<>();
    private final AtomicInteger stepSeq = new AtomicInteger();
    private Long lastNoteId;
    private Long lastTodoId;

    private AgentExecutionContext(Long userId, Long workspaceId, boolean dryRun) {
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.dryRun = dryRun;
    }

    public static AgentExecutionContext begin(Long userId, Long workspaceId, boolean dryRun) {
        AgentExecutionContext context = new AgentExecutionContext(userId, workspaceId, dryRun);
        HOLDER.set(context);
        return context;
    }

    public static AgentExecutionContext current() {
        AgentExecutionContext context = HOLDER.get();
        if (context == null) {
            throw new IllegalStateException("AgentExecutionContext 未初始化");
        }
        return context;
    }

    public static AgentExecutionContext currentOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public AiAgentStepVO recordStep(
            String tool,
            Map<String, Object> input,
            String output,
            boolean requiresConfirm,
            Map<String, Object> actionPayload
    ) {
        AiAgentStepVO step = new AiAgentStepVO();
        step.setId("step-" + stepSeq.incrementAndGet());
        step.setTool(tool);
        step.setInput(input);
        step.setOutput(output);
        step.setRequiresConfirm(requiresConfirm);
        step.setActionPayload(actionPayload);
        step.setStatus(requiresConfirm && dryRun ? "pending_confirm" : "done");
        steps.add(step);
        return step;
    }

    public void setLastNoteId(Long lastNoteId) {
        this.lastNoteId = lastNoteId;
    }

    public Long getLastNoteId() {
        return lastNoteId;
    }

    public void addTodoId(Long todoId) {
        this.lastTodoId = todoId;
    }

    public Long getLastTodoId() {
        return lastTodoId;
    }
}
