package com.eyki.offerpilot.auth.service;

import com.eyki.offerpilot.auth.dto.*;
import com.eyki.offerpilot.auth.domain.User;

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