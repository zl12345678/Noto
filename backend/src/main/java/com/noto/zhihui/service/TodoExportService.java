package com.noto.zhihui.service;

public interface TodoExportService {

    String exportTodosAsIcs(Long userId, Long workspaceId);
}
