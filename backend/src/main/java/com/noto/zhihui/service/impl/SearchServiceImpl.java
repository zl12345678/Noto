package com.noto.zhihui.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.util.NoteTextUtils;
import com.noto.zhihui.common.util.TextMatchRange;
import com.noto.zhihui.entity.WorkspaceEntity;
import com.noto.zhihui.service.NoteService;
import com.noto.zhihui.service.SearchService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.note.NoteVO;
import com.noto.zhihui.vo.search.SearchResultVO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SearchServiceImpl implements SearchService {

    private final NoteService noteService;
    private final WorkspaceService workspaceService;

    public SearchServiceImpl(NoteService noteService, WorkspaceService workspaceService) {
        this.noteService = noteService;
        this.workspaceService = workspaceService;
    }

    @Override
    public Page<SearchResultVO> search(
            Long userId,
            long page,
            long size,
            String keyword,
            Long workspaceId,
            Long folderId,
            Long tagId
    ) {
        if (keyword == null || keyword.isBlank()) {
            return emptyPage(page, size);
        }

        String trimmed = keyword.trim();
        Page<NoteVO> notePage = noteService.pageNotes(
                userId, page, size, trimmed, null, null, workspaceId, folderId, tagId, false
        );

        List<Long> workspaceIds = notePage.getRecords().stream()
                .map(NoteVO::getWorkspaceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> workspaceNames = workspaceIds.isEmpty()
                ? Map.of()
                : workspaceService.listByIds(workspaceIds).stream()
                .collect(Collectors.toMap(WorkspaceEntity::getId, WorkspaceEntity::getName, (a, b) -> a));

        Page<SearchResultVO> resultPage = new Page<>(notePage.getCurrent(), notePage.getSize(), notePage.getTotal());
        resultPage.setRecords(notePage.getRecords().stream()
                .map(note -> toSearchResult(note, trimmed, workspaceNames))
                .toList());
        return resultPage;
    }

    private SearchResultVO toSearchResult(NoteVO note, String keyword, Map<Long, String> workspaceNames) {
        String source = pickHighlightSource(note, keyword);
        String snippet = NoteTextUtils.highlightSnippet(source, keyword, 120);
        String highlight = NoteTextUtils.highlightHtml(source, keyword);
        Integer offsetStart = null;
        Integer offsetEnd = null;
        if (note.getContent() != null && !note.getContent().isBlank()) {
            TextMatchRange range = NoteTextUtils.findMatchRange(note.getContent(), keyword, 120);
            if (range != null && range.start() != null && range.end() != null) {
                offsetStart = range.start();
                offsetEnd = range.end();
            }
        }
        if (offsetStart == null && note.getTitle() != null) {
            TextMatchRange titleRange = NoteTextUtils.findMatchRange(note.getTitle(), keyword, 120);
            if (titleRange != null && titleRange.start() != null && titleRange.end() != null) {
                offsetStart = titleRange.start();
                offsetEnd = titleRange.end();
            }
        }
        String workspaceName = note.getWorkspaceId() == null
                ? null
                : workspaceNames.get(note.getWorkspaceId());
        return new SearchResultVO(
                note.getId(),
                note.getTitle(),
                snippet,
                highlight,
                note.getWorkspaceId(),
                workspaceName,
                note.getFolderId(),
                note.getLastEditedAt(),
                note.getTags(),
                offsetStart,
                offsetEnd
        );
    }

    private String pickHighlightSource(NoteVO note, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        if (note.getTitle() != null && note.getTitle().toLowerCase().contains(lowerKeyword)) {
            return note.getTitle();
        }
        if (note.getContent() != null && !note.getContent().isBlank()) {
            return note.getContent();
        }
        if (note.getSummary() != null && !note.getSummary().isBlank()) {
            return note.getSummary();
        }
        return note.getExcerpt() == null ? "" : note.getExcerpt();
    }

    private Page<SearchResultVO> emptyPage(long page, long size) {
        Page<SearchResultVO> empty = new Page<>(page, size, 0);
        empty.setRecords(List.of());
        return empty;
    }
}
