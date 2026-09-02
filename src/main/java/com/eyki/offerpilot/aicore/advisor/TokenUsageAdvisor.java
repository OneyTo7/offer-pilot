package com.eyki.offerpilot.aicore.advisor;

import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

/**
 * 统一的 LLM Token 用量控制 Advisor。
 *
 * <p><b>前置校验（before）</b>：请求进入 LLM 前，检查用户当月 token 额度是否足够，
 * 不足直接抛 429 业务异常，避免无效的模型调用消耗费用。</p>
 *
 * <p><b>后置累计（after）</b>：LLM 响应完成后，从响应 metadata 提取 token 用量并累计入库
 * （流式场景取最后一个 chunk 的 usage）。累计失败不影响主流程。</p>
 *
 * <p>通过 advisor context 的 {@code user_id} 参数激活：未传 user_id 的调用不校验也不累计。
 * context 含 {@code api_key}（用户自备 key，ApiKeyRoutingAdvisor 直连）时跳过前置额度校验，
 * 仅保留后置用量累计——自备 key 不受平台免费额度限制，用量仍作展示统计。</p>
 */
public class TokenUsageAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);

    private static final int ORDER = 0;
    private static final String CTX_USER_ID = "user_id";
    private static final String CTX_API_KEY = "api_key";

    private final UserTokenUsageService tokenUsageService;

    public TokenUsageAdvisor(UserTokenUsageService tokenUsageService) {
        this.tokenUsageService = tokenUsageService;
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Long userId = extractUserId(request);
        // 自备 API key 的用户不受平台免费额度限制（用量仅作展示统计），跳过前置校验
        if (userId != null && !hasOwnApiKey(request)) {
            tokenUsageService.checkRemainingOrThrow(userId);
        }
        ChatClientResponse response = chain.nextCall(request);
        recordUsage(userId, response);
        return response;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        Long userId = extractUserId(request);
        // 自备 API key 的用户不受平台免费额度限制（用量仅作展示统计），跳过前置校验
        if (userId != null && !hasOwnApiKey(request)) {
            tokenUsageService.checkRemainingOrThrow(userId);
        }
        // 流式响应的 usage 位于最后一个 chunk 的 metadata 中
        AtomicReference<ChatClientResponse> lastResponse = new AtomicReference<>();
        return chain.nextStream(request)
            .doOnNext(lastResponse::set)
            .doOnComplete(() -> recordUsage(userId, lastResponse.get()));
    }

    /**
     * 从 advisor context 提取 userId；未传 user_id 返回 null（跳过校验与累计）。
     */
    private Long extractUserId(ChatClientRequest request) {
        Object userId = request.context().get(CTX_USER_ID);
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        if (userId instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 是否用户自备 API key（ApiKeyRoutingAdvisor 将直连），不受平台免费额度限制。
     */
    private boolean hasOwnApiKey(ChatClientRequest request) {
        Object apiKey = request.context().get(CTX_API_KEY);
        return apiKey instanceof String s && !s.isBlank();
    }

    /**
     * 从 ChatClientResponse 的 metadata 提取 token 用量并累计（失败仅告警，不影响主流程）。
     */
    private void recordUsage(Long userId, ChatClientResponse response) {
        if (userId == null || response == null || response.chatResponse() == null) {
            return;
        }
        try {
            var usage = response.chatResponse().getMetadata().getUsage();
            if (usage != null) {
                int promptTokens = Objects.requireNonNullElse(usage.getPromptTokens(), 0);
                int completionTokens = Objects.requireNonNullElse(usage.getCompletionTokens(), 0);
                tokenUsageService.record(userId, promptTokens, completionTokens);
            }
        } catch (Exception e) {
            log.warn("Token 用量累计失败: userId={}", userId, e);
        }
    }
}
