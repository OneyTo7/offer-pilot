package com.eyki.offerpilot.aicore.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import reactor.core.publisher.Flux;

/**
 * 集成测试：通过真实 ChatClient + Advisor 链验证 TokenUsageAdvisor 的
 * 前置额度校验（checkRemainingOrThrow）与后置用量累计（record）在
 * {@code spec.advisors(param)} → {@code ChatClientRequest.context()} 全链路生效。
 */
class TokenUsageAdvisorTest {

    private UserTokenUsageService tokenUsageService;
    private ChatModel chatModel;
    private ChatClient chatClient;
    private ChatResponse chatResponse;

    @BeforeEach
    void setUp() {
        tokenUsageService = mock(UserTokenUsageService.class);
        chatModel = mock(ChatModel.class);

        // 模型返回带 usage 的响应
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        chatResponse = mock(ChatResponse.class);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatModel.getOptions()).thenReturn(DeepSeekChatOptions.builder().build());

        chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new TokenUsageAdvisor(tokenUsageService))
            .build();
    }

    @Test
    void adviseCall_shouldCheckQuotaAndRecordUsage_whenUserIdInContext() {
        chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> advisor.param("user_id", 1L))
            .call()
            .chatResponse();

        verify(tokenUsageService).checkRemainingOrThrow(1L);
        verify(tokenUsageService).record(1L, 10, 5);
    }

    @Test
    void adviseCall_shouldSkipQuotaCheck_butStillRecord_whenOwnApiKey() {
        // 自备 API key（ApiKeyRoutingAdvisor 直连）：不受平台免费额度限制，
        // 跳过前置校验，但仍保留后置用量累计（展示统计）
        chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> {
                advisor.param("user_id", 1L);
                advisor.param("api_key", "sk-test");
            })
            .call()
            .chatResponse();

        verify(tokenUsageService, never()).checkRemainingOrThrow(1L);
        verify(tokenUsageService).record(1L, 10, 5);
    }

    @Test
    void adviseCall_shouldNotRecordUsage_whenNoUserId() {
        chatClient.prompt()
            .system("system")
            .user("user")
            .call()
            .chatResponse();

        verify(tokenUsageService, never()).checkRemainingOrThrow(anyLong());
        verify(tokenUsageService, never()).record(anyLong(), anyInt(), anyInt());
    }

    @Test
    void adviseCall_shouldThrow429_andSkipModelCall_whenQuotaExceeded() {
        doThrow(BusinessException.of(ErrorCode.TOO_MANY_REQUESTS, "quota exceeded"))
            .when(tokenUsageService).checkRemainingOrThrow(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> advisor.param("user_id", 1L))
            .call()
            .chatResponse());

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getCode());
        verify(chatModel, never()).call(any(Prompt.class));
        verify(tokenUsageService, never()).record(anyLong(), anyInt(), anyInt());
    }

    @Test
    void adviseStream_shouldRecordUsageOnComplete_whenUserIdInContext() {
        // 真实流式场景：最后一个 chunk 携带 usage 的 ChatResponse
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse));

        chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> advisor.param("user_id", 1L))
            .stream()
            .chatResponse()
            .blockLast();

        verify(tokenUsageService).checkRemainingOrThrow(1L);
        verify(tokenUsageService).record(1L, 10, 5);
    }

    @Test
    void adviseStream_shouldThrow429_andSkipModelCall_whenQuotaExceeded() {
        doThrow(BusinessException.of(ErrorCode.TOO_MANY_REQUESTS, "quota exceeded"))
            .when(tokenUsageService).checkRemainingOrThrow(1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> advisor.param("user_id", 1L))
            .stream()
            .chatResponse()
            .blockLast());

        assertEquals(ErrorCode.TOO_MANY_REQUESTS, ex.getCode());
        verify(chatModel, never()).stream(any(Prompt.class));
        verify(tokenUsageService, never()).record(anyLong(), anyInt(), anyInt());
    }
}
