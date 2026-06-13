package com.noto.zhihui.service;

import com.noto.zhihui.vo.ai.AiWeeklyRetroVO;

public interface AiWeeklyRetroService {

    AiWeeklyRetroVO getThisWeekRetro(Long userId);

    AiWeeklyRetroVO generateWeeklyRetro(Long userId, Long workspaceId);

    void runScheduledWeeklyRetros(int hour);
}
