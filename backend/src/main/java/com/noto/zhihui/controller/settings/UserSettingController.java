package com.noto.zhihui.controller.settings;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.settings.AiUserSettingsUpdateRequest;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.UserSettingService;
import com.noto.zhihui.vo.settings.AiUserSettingsVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/ai")
public class UserSettingController {

    private final UserSettingService userSettingService;

    public UserSettingController(UserSettingService userSettingService) {
        this.userSettingService = userSettingService;
    }

    @GetMapping
    public ApiResponse<AiUserSettingsVO> getAiSettings() {
        return ApiResponse.success(userSettingService.getAiSettings(requireUserId()), null);
    }

    @PatchMapping
    public ApiResponse<AiUserSettingsVO> updateAiSettings(@Valid @RequestBody AiUserSettingsUpdateRequest request) {
        return ApiResponse.success(userSettingService.updateAiSettings(requireUserId(), request), null);
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }
}
