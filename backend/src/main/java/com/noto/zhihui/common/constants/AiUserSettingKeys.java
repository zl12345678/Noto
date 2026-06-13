package com.noto.zhihui.common.constants;

public final class AiUserSettingKeys {

    public static final String AUTO_SUMMARY_ON_SAVE = "ai.auto_summary_on_save";
    public static final String DEFAULT_SCOPE = "ai.default_scope";
    public static final String ANSWER_STYLE = "ai.answer_style";
    public static final String DAILY_DIGEST_ENABLED = "ai.daily_digest_enabled";
    public static final String DAILY_DIGEST_HOUR = "ai.daily_digest_hour";
    /** 简单办事（单步待办/提醒）自动执行，跳过二次确认 */
    public static final String AGENT_TRUST_MODE = "ai.agent_trust_mode";
    /** 常用知识库 ID */
    public static final String PRIMARY_WORKSPACE_ID = "ai.primary_workspace_id";
    /** 主攻项目 JSON 数组，最多 5 项 */
    public static final String FOCUS_PROJECTS = "ai.focus_projects";
    /** 办事偏好自由文本 */
    public static final String AGENT_PREFERENCES = "ai.agent_preferences";
    /** 每周五自动生成复盘笔记 */
    public static final String WEEKLY_RETRO_ENABLED = "ai.weekly_retro_enabled";
    public static final String WEEKLY_RETRO_HOUR = "ai.weekly_retro_hour";
    /** 会议类笔记保存后自动弹出待办提取预览 */
    public static final String AUTO_EXTRACT_TODOS_ON_SAVE = "ai.auto_extract_todos_on_save";

    private AiUserSettingKeys() {
    }
}
