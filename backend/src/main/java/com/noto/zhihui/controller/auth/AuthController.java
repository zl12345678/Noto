package com.noto.zhihui.controller.auth;

import com.noto.zhihui.common.api.ApiResponse;
import com.noto.zhihui.common.exception.BizException;
import com.noto.zhihui.common.exception.ErrorCode;
import com.noto.zhihui.dto.auth.ChangePasswordRequest;
import com.noto.zhihui.dto.auth.LoginRequest;
import com.noto.zhihui.dto.auth.RegisterRequest;
import com.noto.zhihui.dto.auth.UpdateAvatarRequest;
import com.noto.zhihui.dto.auth.UpdateProfileRequest;
import com.noto.zhihui.entity.UserEntity;
import com.noto.zhihui.security.JwtTokenService;
import com.noto.zhihui.security.UserContext;
import com.noto.zhihui.service.UserService;
import com.noto.zhihui.service.WorkspaceService;
import com.noto.zhihui.vo.auth.CurrentUserVO;
import com.noto.zhihui.vo.auth.LoginResponse;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.springframework.context.annotation.DependsOn;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@DependsOn("databaseSchemaMigrator")
public class AuthController {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    private static final String DEFAULT_ADMIN_EMAIL = "admin@noto.com";
    private static final String DEFAULT_ADMIN_NICKNAME = "管理员";

    private final JwtTokenService jwtTokenService;
    private final UserService userService;
    private final WorkspaceService workspaceService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            JwtTokenService jwtTokenService,
            UserService userService,
            WorkspaceService workspaceService,
            PasswordEncoder passwordEncoder
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
        this.workspaceService = workspaceService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void initDefaultAdmin() {
        UserEntity admin = userService.findByUsername(DEFAULT_ADMIN_USERNAME);
        if (admin == null) {
            UserEntity user = new UserEntity();
            user.setUsername(DEFAULT_ADMIN_USERNAME);
            user.setEmail(DEFAULT_ADMIN_EMAIL);
            user.setNickname(DEFAULT_ADMIN_NICKNAME);
            user.setPasswordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            user.setStatus(0);
            userService.save(user);
            workspaceService.createDefaultWorkspace(user.getId());
        } else {
            workspaceService.ensureDefaultWorkspace(admin.getId());
        }
    }

    @Transactional
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        UserEntity user = userService.findByUsername(request.getUsername());
        if (user == null || (user.getStatus() != null && user.getStatus() != 0)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        user.setLastLoginAt(java.time.LocalDateTime.now());
        userService.updateById(user);

        String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
        return ApiResponse.success(buildLoginResponse(token, user), null);
    }

    @Transactional
    @PostMapping("/register")
    public ApiResponse<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        if (userService.findByUsername(request.getUsername()) != null) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (userService.findByEmail(request.getEmail()) != null) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setNickname(request.getNickname());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(0);
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userService.save(user);
        workspaceService.createDefaultWorkspace(user.getId());

        String token = jwtTokenService.generateToken(user.getId(), user.getUsername());
        return ApiResponse.success(buildLoginResponse(token, user), null);
    }

    private LoginResponse buildLoginResponse(String token, UserEntity user) {
        CurrentUserVO currentUser = new CurrentUserVO(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getAvatarUrl()
        );
        return new LoginResponse(token, "Bearer", 604800L, currentUser);
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserVO> me() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return ApiResponse.success(new CurrentUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getAvatarUrl()), null);
    }

    @PostMapping("/profile")
    public ApiResponse<CurrentUserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        user.setNickname(request.getNickname());
        userService.updateById(user);
        return ApiResponse.success(new CurrentUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getAvatarUrl()), null);
    }

    @PostMapping("/avatar")
    public ApiResponse<CurrentUserVO> updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        validateAvatarUrl(request.getAvatarUrl());
        UserEntity user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        user.setAvatarUrl(request.getAvatarUrl());
        userService.updateById(user);
        return ApiResponse.success(new CurrentUserVO(user.getId(), user.getUsername(), user.getNickname(), user.getEmail(), user.getAvatarUrl()), null);
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        UserEntity user = userService.getById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userService.updateById(user);
        return ApiResponse.success(null, null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        return ApiResponse.success(null, null);
    }

    private void validateAvatarUrl(String avatarUrl) {
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return;
        }
        String trimmed = avatarUrl.trim();
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://") || trimmed.startsWith("/"))) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "头像地址仅支持 http(s) 或站内路径");
        }
        if (trimmed.toLowerCase().startsWith("javascript:") || trimmed.toLowerCase().startsWith("data:")) {
            throw new BizException(ErrorCode.BAD_REQUEST.getCode(), "无效的头像地址");
        }
    }
}
