package com.noto.zhihui.service;

import com.noto.zhihui.vo.ai.AiDigestVO;

public interface AiDigestService {

    AiDigestVO getTodayDigest(Long userId);

    AiDigestVO generateDigest(Long userId, Long workspaceId);

    void runScheduledDigests(int hour);
}
