package com.eyki.offerpilot.aicore.controller;

import com.eyki.offerpilot.aicore.usage.domain.UserTokenUsage;
import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.model.ApiResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 大模型模块控制器。
 *
 * <p>提供 API Key 状态、Token 用量、套餐信息等聚合数据，供前端大模型配置页面使用。</p>
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiModelController {

    private final AuthService authService;
    private final UserTokenUsageService tokenUsageService;

    @Value("${offer-pilot.plan.free.monthly-tokens:100000}")
    private int freeMonthlyTokenLimit;

    @Value("${offer-pilot.plan.free.daily-reports:3}")
    private int freeDailyReportLimit;

    @Value("${offer-pilot.plan.free.daily-interviews:1}")
    private int freeDailyInterviewLimit;

    @Value("${offer-pilot.community.github-url:}")
    private String githubUrl;

    @Value("${offer-pilot.community.wechat-qr-url:}")
    private String wechatQrUrl;

    @Value("${offer-pilot.community.author-wechat:}")
    private String authorWechat;

    /**
     * 获取大模型模块整体信息：API Key 状态、用量摘要、套餐信息、社区信息。
     */
    @GetMapping("/info")
    public ApiResult<AiInfoVO> getInfo() {
        User user = authService.getCurrentUserEntity();
        Long userId = user.getId();

        // API Key 信息
        String apiKey = user.getApiKey();
        String maskedKey = null;
        if (apiKey != null && !apiKey.isBlank()) {
            // 只显示前 4 位和后 4 位，中间隐藏
            if (apiKey.length() > 10) {
                maskedKey = apiKey.substring(0, 5) + "***" + apiKey.substring(apiKey.length() - 4);
            } else {
                maskedKey = apiKey.substring(0, Math.min(2, apiKey.length())) + "***";
            }
        }

        // 用量信息
        UserTokenUsage todayUsage = tokenUsageService.getTodayUsage(userId);
        long monthlyTotal = tokenUsageService.getMonthlyTotalTokens(userId);
        long remaining = tokenUsageService.getRemainingTokens(userId);
        double percent = tokenUsageService.getUsagePercent(userId);
        List<UserTokenUsage> monthlyUsage = tokenUsageService.getMonthlyUsage(userId);

        // 每日趋势
        List<DailyUsageVO> dailyUsage = monthlyUsage.stream()
            .map(u -> new DailyUsageVO(u.getUsageDate(), u.getTotalTokens()))
            .collect(Collectors.toList());

        // 今日用量
        UsageVO today = new UsageVO(
            todayUsage != null ? todayUsage.getTotalTokens() : 0,
            todayUsage != null ? todayUsage.getPromptTokens() : 0,
            todayUsage != null ? todayUsage.getCompletionTokens() : 0
        );

        // 套餐信息
        PlanVO plan = new PlanVO("free", freeMonthlyTokenLimit, freeDailyReportLimit, freeDailyInterviewLimit,
            remaining, monthlyTotal, percent);

        // 社区信息
        CommunityVO community = new CommunityVO(githubUrl, wechatQrUrl, authorWechat);

        return ApiResult.success(new AiInfoVO(
            new ApiKeyVO(apiKey != null && !apiKey.isBlank(), maskedKey, user.getUpdatedAt()),
            new UsageSummaryVO(today, monthlyTotal, dailyUsage),
            plan, community
        ));
    }

    // ========== Inner DTOs ==========

    private record ApiKeyVO(
        @JsonProperty("has_key") boolean hasKey,
        @JsonProperty("key_masked") String keyMasked,
        @JsonProperty("updated_at") LocalDateTime updatedAt
    ) {}

    private record UsageVO(
        @JsonProperty("total_tokens") int totalTokens,
        @JsonProperty("prompt_tokens") int promptTokens,
        @JsonProperty("completion_tokens") int completionTokens
    ) {}

    private record DailyUsageVO(
        LocalDate date,
        @JsonProperty("total_tokens") int totalTokens
    ) {}

    private record UsageSummaryVO(
        UsageVO today,
        @JsonProperty("monthly_total") long monthlyTotal,
        @JsonProperty("daily") List<DailyUsageVO> daily
    ) {}

    private record PlanVO(
        String type,
        @JsonProperty("monthly_token_limit") int monthlyTokenLimit,
        @JsonProperty("daily_report_limit") int dailyReportLimit,
        @JsonProperty("daily_interview_limit") int dailyInterviewLimit,
        @JsonProperty("remaining_tokens") long remainingTokens,
        @JsonProperty("used_tokens") long usedTokens,
        @JsonProperty("usage_percent") double usagePercent
    ) {}

    private record CommunityVO(
        @JsonProperty("github_url") String githubUrl,
        @JsonProperty("wechat_qr_url") String wechatQrUrl,
        @JsonProperty("author_wechat") String authorWechat
    ) {}

    private record AiInfoVO(
        @JsonProperty("api_key") ApiKeyVO apiKey,
        UsageSummaryVO usage,
        PlanVO plan,
        CommunityVO community
    ) {}
}