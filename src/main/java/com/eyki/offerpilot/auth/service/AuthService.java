package com.eyki.offerpilot.auth.service;

import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.dto.LoginRequest;
import com.eyki.offerpilot.auth.dto.RefreshRequest;
import com.eyki.offerpilot.auth.dto.RegisterRequest;
import com.eyki.offerpilot.auth.dto.TokenResponse;
import com.eyki.offerpilot.auth.dto.UpdateApiKeyRequest;
import com.eyki.offerpilot.auth.dto.UpdateProfileRequest;
import com.eyki.offerpilot.auth.dto.UserVO;

public interface AuthService {

    TokenResponse register(RegisterRequest request);

    TokenResponse login(LoginRequest request);

    TokenResponse refresh(RefreshRequest request);

    void logout();

    UserVO getCurrentUser();

    UserVO updateProfile(UpdateProfileRequest request);

    UserVO updateApiKey(UpdateApiKeyRequest request);

    User getCurrentUserEntity();
}