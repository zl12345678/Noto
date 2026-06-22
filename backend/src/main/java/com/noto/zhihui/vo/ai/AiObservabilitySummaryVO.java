package com.noto.zhihui.vo.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiObservabilitySummaryVO {

    private long totalCalls;

    private double successRate;

    private Long averageLatencyMs;

    private List<AiObservabilityActionVO> recentActions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiObservabilityActionVO {

        private String actionType;

        private String resourceType;

        private boolean success;

        private Long latencyMs;

        private LocalDateTime createdAt;
    }
}
