package com.eyki.offerpilot.aicore.usage.service;

import com.eyki.offerpilot.aicore.usage.domain.UserTokenUsage;
import com.eyki.offerpilot.aicore.usage.repository.UserTokenUsageRepository;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户 LLM Token 用量管理服务。
 *
 * <p>负责记录每次 LLM 调用的 token 消耗、查询用量统计、以及前置检查是否超限。
 * 免费用户每月有 token 上限，配置自己的 API Key 的用户不限制。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserTokenUsageService {

    private final UserTokenUsageRepository repository;

    @Value("${offer-pilot.plan.free.monthly-tokens:100000}")
    private int freeMonthlyTokenLimit;

    /**
     * 记录 token 用量（异步调用，不抛异常）。
     *
     * @param userId           用户 ID
     * @param promptTokens     输入 token 数
     * @param completionTokens 输出 token 数
     */
    /**
     * 同步锁防竞态：MVP 单实例部署时，两个并发请求可能同时 findByUserIdAndDate 为空
     * 导致重复插入（token 用量丢失）。用 synchronized 加锁，后续迁移到 Redis 可替换。
     */
    @Transactional
    public synchronized void record(Long userId, int promptTokens, int completionTokens) {
        try {
            LocalDate today = LocalDate.now();
            int totalTokens = promptTokens + completionTokens;
            Optional<UserTokenUsage> existing = repository.findByUserIdAndDate(userId, today);

            if (existing.isPresent()) {
                UserTokenUsage usage = existing.get();
                usage.setTotalTokens(usage.getTotalTokens() + totalTokens);
                usage.setPromptTokens(usage.getPromptTokens() + promptTokens);
                usage.setCompletionTokens(usage.getCompletionTokens() + completionTokens);
                usage.setUpdatedAt(LocalDateTime.now());
                repository.updateById(usage);
                log.debug("Token 用量更新: userId={}, totalTokens={}, existing={}, new={}",
                    userId, usage.getTotalTokens(), existing.get().getTotalTokens(), totalTokens);
            } else {
                UserTokenUsage usage = new UserTokenUsage();
                usage.setUserId(userId);
                usage.setUsageDate(today);
                usage.setTotalTokens(totalTokens);
                usage.setPromptTokens(promptTokens);
                usage.setCompletionTokens(completionTokens);
                usage.setCreatedAt(LocalDateTime.now());
                usage.setUpdatedAt(LocalDateTime.now());
                repository.insert(usage);
                log.debug("Token 用量记录: userId={}, totalTokens={}", userId, totalTokens);
            }
        } catch (Exception e) {
            // 用量记录是辅助功能，不应影响主流程
            log.error("Token 用量记录失败: userId={}", userId, e);
        }
    }

    /**
     * 检查用户是否有足够的 token 额度。
     *
     * @param userId 用户 ID
     * @return true = 有剩余额度，false = 已超限
     */
    public boolean checkRemaining(Long userId) {
        long used = getMonthlyTotalTokens(userId);
        boolean hasRemaining = used < freeMonthlyTokenLimit;
        log.debug("Token 额度检查: userId={}, used={}, limit={}, remaining={}",
            userId, used, freeMonthlyTokenLimit, hasRemaining);
        return hasRemaining;
    }

    /**
     * 检查用户是否有足够的 token 额度，不足时抛 429 业务异常。
     * 供统一 TokenUsageAdvisor 前置校验与各服务入口调用。
     */
    public void checkRemainingOrThrow(Long userId) {
        if (!checkRemaining(userId)) {
            throw BusinessException.of(ErrorCode.TOO_MANY_REQUESTS,
                "本月 Token 额度已用完（免费版每月限 " + freeMonthlyTokenLimit + " tokens），"
                    + "可配置自己的 DeepSeek API Key 解锁无限使用");
        }
    }

    /**
     * 获取用户本月已使用的 token 总量。
     */
    public long getMonthlyTotalTokens(Long userId) {
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        Integer sum = repository.findByUserIdAndDateBetween(userId, start, now).stream()
            .mapToInt(UserTokenUsage::getTotalTokens)
            .sum();
        return sum;
    }

    /**
     * 获取今日用量。
     */
    public UserTokenUsage getTodayUsage(Long userId) {
        return repository.findByUserIdAndDate(userId, LocalDate.now()).orElse(null);
    }

    /**
     * 获取本月每日用量明细（用于前端趋势图）。
     */
    public List<UserTokenUsage> getMonthlyUsage(Long userId) {
        return repository.findByUserIdCurrentMonth(userId);
    }

    /**
     * 获取用户本月剩余 token 数。
     */
    public long getRemainingTokens(Long userId) {
        return Math.max(0, freeMonthlyTokenLimit - getMonthlyTotalTokens(userId));
    }

    /**
     * 获取用户本月 token 使用百分比（0-100）。
     */
    public double getUsagePercent(Long userId) {
        long used = getMonthlyTotalTokens(userId);
        if (used == 0) return 0;
        return Math.min(100.0, (double) used / freeMonthlyTokenLimit * 100);
    }
}