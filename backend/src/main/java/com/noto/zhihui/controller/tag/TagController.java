package com.noto.zhihui.controller.tag;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.tag.TagCreateRequest;
import com.noto.zhihui.entity.TagEntity;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.TagService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.tag.TagVO;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private static final List<String> DEFAULT_COLORS = List.of(
            "#1677ff", "#52c41a", "#faad14", "#eb2f96", "#722ed1", "#13c2c2"
    );

    private final TagService tagService;
    private final WorkspaceService workspaceService;

    public TagController(TagService tagService, WorkspaceService workspaceService) {
        this.tagService = tagService;
        this.workspaceService = workspaceService;
    }

    @GetMapping
    public ApiResponse<List<TagVO>> list(@RequestParam Long workspaceId) {
        workspaceService.requireOwnedWorkspace(workspaceId, requireUserId());
        List<TagVO> tags = tagService.lambdaQuery()
                .eq(TagEntity::getWorkspaceId, workspaceId)
                .orderByAsc(TagEntity::getName)
                .list()
                .stream()
                .map(this::toVO)
                .toList();
        return ApiResponse.success(tags, null);
    }

    @Transactional
    @PostMapping
    public ApiResponse<TagVO> create(@Valid @RequestBody TagCreateRequest request) {
        Long userId = requireUserId();
        workspaceService.requireOwnedWorkspace(request.getWorkspaceId(), userId);
        if (tagService.lambdaQuery()
                .eq(TagEntity::getWorkspaceId, request.getWorkspaceId())
                .eq(TagEntity::getName, request.getName().trim())
                .exists()) {
            throw new BizException(ErrorCode.TAG_EXISTS);
        }
        TagEntity tag = new TagEntity();
        tag.setWorkspaceId(request.getWorkspaceId());
        tag.setName(request.getName().trim());
        tag.setColor(resolveColor(request.getColor(), request.getWorkspaceId()));
        tagService.save(tag);
        return ApiResponse.success(toVO(tag), null);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        TagEntity tag = tagService.getById(id);
        if (tag == null) {
            throw new BizException(ErrorCode.TAG_NOT_FOUND);
        }
        workspaceService.requireOwnedWorkspace(tag.getWorkspaceId(), requireUserId());
        tagService.removeById(id);
        return ApiResponse.success(null, null);
    }

    private String resolveColor(String color, Long workspaceId) {
        if (color != null && !color.isBlank()) {
            return color.trim();
        }
        long count = tagService.lambdaQuery().eq(TagEntity::getWorkspaceId, workspaceId).count();
        return DEFAULT_COLORS.get((int) (count % DEFAULT_COLORS.size()));
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private TagVO toVO(TagEntity tag) {
        return new TagVO(tag.getId(), tag.getWorkspaceId(), tag.getName(), tag.getColor());
    }
}
