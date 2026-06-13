package com.noto.zhihui.controller.reminder;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.reminder.ReminderCreateRequest;
import com.noto.zhihui.dto.reminder.ReminderUpdateRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.ReminderService;
import com.noto.zhihui.vo.reminder.ReminderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping("/due")
    public ApiResponse<List<ReminderVO>> due() {
        return ApiResponse.success(reminderService.deliverDueReminders(requireUserId()), null);
    }

    @GetMapping
    public ApiResponse<Page<ReminderVO>> list(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long todoId
    ) {
        return ApiResponse.success(
                reminderService.pageReminders(requireUserId(), page, size, workspaceId, status, todoId),
                null
        );
    }

    @PostMapping
    public ApiResponse<ReminderVO> create(@Valid @RequestBody ReminderCreateRequest request) {
        return ApiResponse.success(reminderService.createReminder(request, requireUserId()), null);
    }

    @PutMapping("/{id}")
    public ApiResponse<ReminderVO> update(@PathVariable Long id, @Valid @RequestBody ReminderUpdateRequest request) {
        return ApiResponse.success(reminderService.updateReminder(id, request, requireUserId()), null);
    }

    @PatchMapping("/{id}/cancel")
    public ApiResponse<ReminderVO> cancel(@PathVariable Long id) {
        return ApiResponse.success(reminderService.cancelReminder(id, requireUserId()), null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        reminderService.deleteReminder(id, requireUserId());
        return ApiResponse.success(null, null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
