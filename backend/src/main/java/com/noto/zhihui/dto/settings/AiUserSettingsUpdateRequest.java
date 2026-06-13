package com.noto.zhihui.dto.settings;

import lombok.Data;

@Data
public class AiUserSettingsUpdateRequest {

    /** 保存时自动生成摘要（摘要为空时） */
    private Boolean autoSummaryOnSave;

    /** workspace | note */
    private String defaultScope;

    /** concise | balanced | detailed */
    private String answerStyle;

    /** 每日 AI 行动建议 digest（定时生成） */
    private Boolean dailyDigestEnabled;

    /** 生成时间（0-23 点，默认 8） */
    private Integer dailyDigestHour;

    /** 简单办事信任模式：单步待办/提醒自动执行 */
    private Boolean agentTrustMode;

    private Long primaryWorkspaceId;

    private java.util.List<String> focusProjects;

    private String agentPreferences;

    private Boolean weeklyRetroEnabled;

    private Integer weeklyRetroHour;

    private Boolean autoExtractTodosOnSave;
}
