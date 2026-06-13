package com.noto.zhihui.vo.settings;

import lombok.Data;

@Data
public class AiUserSettingsVO {

    private boolean autoSummaryOnSave;
    private String defaultScope;
    private String answerStyle;
    private boolean dailyDigestEnabled;
    private int dailyDigestHour = 8;
    /** 简单办事自动执行（仅单步 createTodo / createReminder） */
    private boolean agentTrustMode;
    /** 常用知识库 */
    private Long primaryWorkspaceId;
    private String primaryWorkspaceName;
    /** 主攻项目（最多 5 个） */
    private java.util.List<String> focusProjects;
    /** 办事偏好说明 */
    private String agentPreferences;
    private boolean weeklyRetroEnabled;
    private int weeklyRetroHour = 17;
    private boolean autoExtractTodosOnSave;
}
