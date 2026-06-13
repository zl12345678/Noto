package com.noto.zhihui.controller.search;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.SearchService;
import com.noto.zhihui.vo.search.SearchResultVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<Page<SearchResultVO>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long workspaceId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) Long tagId
    ) {
        Long userId = requireUserId();
        return ApiResponse.success(
                searchService.search(userId, page, size, keyword, workspaceId, folderId, tagId),
                null
        );
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
