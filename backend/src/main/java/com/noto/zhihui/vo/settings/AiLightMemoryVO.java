package com.noto.zhihui.vo.settings;

import lombok.Data;

import java.util.List;

/**
 * 用户显式配置的 AI 轻量记忆（非对话历史）。
 */
@Data
public class AiLightMemoryVO {

    private Long primaryWorkspaceId;
    private String primaryWorkspaceName;
    private List<String> focusProjects;
    private String agentPreferences;

    public boolean isEmpty() {
        return (primaryWorkspaceId == null || primaryWorkspaceId <= 0)
                && (focusProjects == null || focusProjects.isEmpty())
                && (agentPreferences == null || agentPreferences.isBlank());
    }

    public String toPromptBlock() {
        if (isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder("【用户偏好记忆】\n");
        if (primaryWorkspaceId != null && primaryWorkspaceId > 0 && primaryWorkspaceName != null) {
            builder.append("常用知识库：").append(primaryWorkspaceName.trim()).append('\n');
        }
        if (focusProjects != null && !focusProjects.isEmpty()) {
            builder.append("当前主攻项目：").append(String.join("、", focusProjects)).append('\n');
        }
        if (agentPreferences != null && !agentPreferences.isBlank()) {
            builder.append("办事偏好：").append(agentPreferences.trim()).append('\n');
        }
        return builder.toString().trim();
    }
}
