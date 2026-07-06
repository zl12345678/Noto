package com.noto.zhihui.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.entity.UserEntity;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.AuditLogService;
import com.noto.zhihui.service.UserService;
import com.noto.zhihui.vo.admin.AdminAuditLogVO;
import com.noto.zhihui.vo.admin.AdminAuditSummaryVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/audit-logs")
public class AdminAuditController {

    private final AuditLogService auditLogService;
    private final UserService userService;

    public AdminAuditController(AuditLogService auditLogService, UserService userService) {
        this.auditLogService = auditLogService;
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<Page<AdminAuditLogVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String ipAddress,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt
    ) {
        requireAdmin();
        return ApiResponse.success(
                auditLogService.listAdminLogs(page, size, userId, actionType, ipAddress, startAt, endAt),
                null
        );
    }

    @GetMapping("/summary")
    public ApiResponse<AdminAuditSummaryVO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt
    ) {
        requireAdmin();
        return ApiResponse.success(auditLogService.adminSummary(startAt, endAt), null);
    }

    private void requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userService.getById(userId);
        if (user == null || !"admin".equals(user.getUsername())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }
}
