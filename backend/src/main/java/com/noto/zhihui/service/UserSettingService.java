package com.noto.zhihui.service;

import com.noto.zhihui.dto.settings.AiUserSettingsUpdateRequest;
import com.noto.zhihui.vo.settings.AiLightMemoryVO;
import com.noto.zhihui.vo.settings.AiUserSettingsVO;

import java.util.List;

public interface UserSettingService {

    AiUserSettingsVO getAiSettings(Long userId);

    AiUserSettingsVO updateAiSettings(Long userId, AiUserSettingsUpdateRequest request);

    String resolveAnswerStyleInstruction(Long userId);

    /** 查询在指定小时开启每日 digest 的用户 ID */
    List<Long> listUserIdsForDailyDigestHour(int hour);

    /** 是否开启办事信任模式 */
    boolean isAgentTrustModeEnabled(Long userId);

    AiLightMemoryVO getLightMemory(Long userId);

    String resolveLightMemoryPromptBlock(Long userId);

    Long resolvePrimaryWorkspaceId(Long userId);

    List<Long> listUserIdsForWeeklyRetroHour(int hour);

    boolean isAutoExtractTodosOnSaveEnabled(Long userId);
}
