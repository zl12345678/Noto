package com.noto.zhihui.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.noto.zhihui.vo.search.SearchResultVO;

public interface SearchService {

    Page<SearchResultVO> search(
            Long userId,
            long page,
            long size,
            String keyword,
            Long workspaceId,
            Long folderId,
            Long tagId
    );
}
