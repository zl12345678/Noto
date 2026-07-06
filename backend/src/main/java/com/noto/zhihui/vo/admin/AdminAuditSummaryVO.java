package com.noto.zhihui.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminAuditSummaryVO {

    private long totalActions;
    private long todayActions;
    private long uniqueUsers;
    private long todayUniqueUsers;
    private long uniqueIps;
    private long todayUniqueIps;
}
