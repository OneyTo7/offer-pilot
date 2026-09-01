package com.eyki.offerpilot.auth.controller;

import com.eyki.offerpilot.auth.dto.LoginRequest;
import com.eyki.offerpilot.auth.dto.RefreshRequest;
import com.eyki.offerpilot.auth.dto.RegisterRequest;
import com.eyki.offerpilot.auth.dto.TokenResponse;
import com.eyki.offerpilot.auth.dto.UpdateApiKeyRequest;
import com.eyki.offerpilot.auth.dto.UpdateProfileRequest;
import com.eyki.offerpilot.auth.dto.UserVO;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.model.ApiResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller — handles user registration, login, token refresh, logout,
 * profile retrieval, and API key management.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResult<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        TokenResponse response = authService.register(request);
        return ApiResult.success("注册成功", response);
    }

    @PostMapping("/login")
    public ApiResult<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenResponse response = authService.login(request);
        return ApiResult.success("登录成功", response);
    }

    @PostMapping("/refresh")
    public ApiResult<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ApiResult.success("令牌刷新成功", response);
    }

    @PostMapping("/logout")
    public ApiResult<?> logout() {
        authService.logout();
        return ApiResult.success("登出成功");
    }

    @GetMapping("/me")
    public ApiResult<UserVO> getCurrentUser() {
        UserVO user = authService.getCurrentUser();
        return ApiResult.success(user);
    }

    @PutMapping("/profile")
    public ApiResult<UserVO> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        UserVO user = authService.updateProfile(request);
        return ApiResult.success("更新成功", user);
    }

    @PutMapping("/api-key")
    public ApiResult<UserVO> updateApiKey(@Valid @RequestBody UpdateApiKeyRequest request) {
        UserVO user = authService.updateApiKey(request);
        return ApiResult.success("API Key 更新成功", user);
    }
}