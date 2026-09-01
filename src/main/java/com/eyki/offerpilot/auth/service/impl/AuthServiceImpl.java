package com.eyki.offerpilot.auth.service.impl;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.dto.LoginRequest;
import com.eyki.offerpilot.auth.dto.RefreshRequest;
import com.eyki.offerpilot.auth.dto.RegisterRequest;
import com.eyki.offerpilot.auth.dto.TokenResponse;
import com.eyki.offerpilot.auth.dto.UpdateApiKeyRequest;
import com.eyki.offerpilot.auth.dto.UpdateProfileRequest;
import com.eyki.offerpilot.auth.dto.UserVO;
import com.eyki.offerpilot.auth.repository.UserRepository;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authentication service implementation. Handles registration (BCrypt password hashing),
 * login, dual-token management (Sa-Token access + in-memory refresh token), logout,
 * and profile/API key updates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TOKEN_TTL = 7 * 24 * 3600L; // 7 days
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";

    private final UserRepository userRepository;

    /**
     * In-memory refresh token store (MVP only — migrate to Redis in production). Maps refresh token -> user ID.
     */
    private final Map<String, RefreshTokenEntry> refreshTokenStore = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw BusinessException.emailAlreadyRegistered();
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setNickname(request.getNickname());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.insert(user);

        log.info("用户注册成功: email={}, userId={}", user.getEmail(), user.getId());
        return generateTokenPair(user);
    }

    @Override
    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> BusinessException.of(ErrorCode.UNAUTHORIZED, "邮箱或密码错误"));

        if (user.getStatus() == 0) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "账号已被禁用");
        }

        if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "邮箱或密码错误");
        }

        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.updateById(user);

        log.info("用户登录成功: email={}, userId={}", user.getEmail(), user.getId());
        return generateTokenPair(user);
    }

    @Override
    public TokenResponse refresh(RefreshRequest request) {
        RefreshTokenEntry entry = refreshTokenStore.get(request.getRefreshToken());
        if (entry == null || entry.expiresAt.isBefore(LocalDateTime.now())) {
            refreshTokenStore.remove(request.getRefreshToken());
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "刷新令牌无效或已过期，请重新登录");
        }

        // Remove old refresh token (one-time use)
        refreshTokenStore.remove(request.getRefreshToken());

        User user = userRepository.selectById(entry.userId);
        if (user == null || user.getStatus() == 0) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "用户不存在或已被禁用");
        }

        log.info("令牌刷新成功: userId={}", user.getId());
        return generateTokenPair(user);
    }

    @Override
    public void logout() {
        StpUtil.logout();
        log.info("用户登出: userId={}", StpUtil.getLoginIdDefaultNull());
    }

    @Override
    public UserVO getCurrentUser() {
        User user = getCurrentUserEntity();
        return toUserVO(user);
    }

    @Override
    @Transactional
    public UserVO updateProfile(UpdateProfileRequest request) {
        User user = getCurrentUserEntity();
        if (request.getNickname() != null) {
            user.setNickname(request.getNickname());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        return toUserVO(user);
    }

    @Override
    @Transactional
    public UserVO updateApiKey(UpdateApiKeyRequest request) {
        User user = getCurrentUserEntity();
        user.setApiKey(request.getApiKey());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.updateById(user);
        return toUserVO(user);
    }

    @Override
    public User getCurrentUserEntity() {
        long userId = StpUtil.getLoginIdAsLong();
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    private TokenResponse generateTokenPair(User user) {
        // Login with Sa-Token to create access token (2h timeout configured in yaml)
        StpUtil.login(user.getId());

        // Generate refresh token
        String refreshToken = IdUtil.fastSimpleUUID();
        refreshTokenStore.put(refreshToken,
            new RefreshTokenEntry(user.getId(), LocalDateTime.now().plusSeconds(REFRESH_TOKEN_TTL)));

        SaTokenInfo tokenInfo = StpUtil.getTokenInfo();
        return TokenResponse.builder().accessToken(tokenInfo.getTokenValue()).refreshToken(refreshToken)
            .expiresIn(tokenInfo.getTokenTimeout()).build();
    }

    private UserVO toUserVO(User user) {
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    private record RefreshTokenEntry(Long userId, LocalDateTime expiresAt) {
    }
}