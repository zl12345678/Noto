package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.noto.zhihui.dto.reminder.ReminderCreateRequest;
import com.noto.zhihui.dto.reminder.ReminderUpdateRequest;
import com.noto.zhihui.entity.ReminderEntity;
import com.noto.zhihui.vo.reminder.ReminderVO;

import java.util.List;

public interface ReminderService extends IService<ReminderEntity> {

    Page<ReminderVO> pageReminders(
            Long userId,
            long page,
            long size,
            Long workspaceId,
            Integer status,
            Long todoId
    );

    ReminderVO createReminder(ReminderCreateRequest request, Long userId);

    ReminderVO updateReminder(Long id, ReminderUpdateRequest request, Long userId);

    ReminderVO cancelReminder(Long id, Long userId);

    void deleteReminder(Long id, Long userId);

    List<ReminderVO> deliverDueReminders(Long userId);
}
