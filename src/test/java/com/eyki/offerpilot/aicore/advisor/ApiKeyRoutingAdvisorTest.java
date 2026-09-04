package com.eyki.offerpilot.aicore.advisor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import reactor.core.publisher.Flux;

/**
 * 集成测试：通过真实 ChatClient + Advisor 链验证 ApiKeyRoutingAdvisor 的路由行为。
 *
 * <p>context 含 {@code api_key} 时拦截模型调用（ChatModel 不被调用）、直连 DeepSeek
 * （mock HttpClient 验证请求构造与响应解析），且 TokenUsageAdvisor 仍能从直连响应
 * 提取 usage 做后置累计；无 api_key 时透传给平台 ChatModel。</p>
 */
class ApiKeyRoutingAdvisorTest {

    private static final String SYNC_BODY = """
        {"choices":[{"message":{"content":"hello world"}}],
         "usage":{"prompt_tokens":10,"completion_tokens":5,"total_tokens":15}}""";

    private static final String SSE_BODY = "data: {\"choices\":[{\"delta\":{\"content\":\"你\"}}]}\n\n"
        + "data: {\"choices\":[{\"delta\":{\"content\":\"好\"}}]}\n\n"
        + "data: {\"choices\":[],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}\n\n"
        + "data: [DONE]\n\n";

    private UserTokenUsageService tokenUsageService;
    private ChatModel chatModel;
    private HttpClient httpClient;
    private ChatClient chatClient;
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        tokenUsageService = mock(UserTokenUsageService.class);
        chatModel = mock(ChatModel.class);
        httpClient = mock(HttpClient.class);
        when(chatModel.getOptions()).thenReturn(OpenAiChatOptions.builder().build());

        chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new TokenUsageAdvisor(tokenUsageService),
                new ApiKeyRoutingAdvisor(objectMapper, httpClient))
            .build();
    }

    private void stubSyncResponse(int statusCode, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(response);
    }

    private void stubStreamResponse(int statusCode, String body) throws Exception {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
        when(httpClient.send(any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any())).thenReturn(response);
    }

    @Test
    void adviseCall_shouldRouteToDeepSeek_whenApiKeyInContext() throws Exception {
        stubSyncResponse(200, SYNC_BODY);

        ChatResponse chatResponse = chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> {
                advisor.param("user_id", 1L);
                advisor.param("api_key", "sk-test");
            })
            .call()
            .chatResponse();

        // 未走平台 ChatModel
        verify(chatModel, never()).call(any(Prompt.class));
        // 直连响应内容正确
        assertEquals("hello world", chatResponse.getResult().getOutput().getText());
        // TokenUsageAdvisor：自备 key 跳过前置额度校验，仅保留后置用量累计
        verify(tokenUsageService, never()).checkRemainingOrThrow(1L);
        verify(tokenUsageService).record(1L, 10, 5);
    }

    @Test
    void adviseCall_shouldSendEnhancedMessagesWithAuthHeader_whenApiKeyInContext() throws Exception {
        stubSyncResponse(200, SYNC_BODY);

        chatClient.prompt()
            .system("system-prompt")
            .user("user-prompt")
            .advisors(advisor -> advisor.param("api_key", "sk-test"))
            .call()
            .chatResponse();

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(requestCaptor.capture(), any());
        HttpRequest sent = requestCaptor.getValue();

        // 请求头携带用户 key
        assertTrue(sent.headers().firstValue("Authorization").orElse("").contains("sk-test"));
        // 请求体包含增强后的完整消息（system + user）
        JsonNode body = objectMapper.readTree(new String(readBody(sent), StandardCharsets.UTF_8));
        JsonNode messages = body.get("messages");
        assertEquals(2, messages.size());
        assertEquals("system", messages.get(0).get("role").asText());
        assertEquals("system-prompt", messages.get(0).get("content").asText());
        assertEquals("user", messages.get(1).get("role").asText());
    }

    @Test
    void adviseCall_shouldPassThroughToChatModel_whenNoApiKey() throws Exception {
        chatClient.prompt()
            .system("system")
            .user("user")
            .call()
            .chatResponse();

        verify(chatModel).call(any(Prompt.class));
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @Test
    void adviseCall_shouldThrowBusinessException_whenHttpError() throws Exception {
        stubSyncResponse(500, "{\"error\":\"boom\"}");

        BusinessException ex = assertThrows(BusinessException.class, () -> chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> advisor.param("api_key", "sk-test"))
            .call()
            .chatResponse());

        assertTrue(ex.getMessage().contains("状态码: 500"));
        verify(chatModel, never()).call(any(Prompt.class));
        verify(tokenUsageService, never()).record(anyLong(), anyInt(), anyInt());
    }

    @Test
    void adviseStream_shouldEmitChunksAndFinalUsage_whenApiKeyInContext() throws Exception {
        stubStreamResponse(200, SSE_BODY);

        List<String> tokens = chatClient.prompt()
            .system("system")
            .user("user")
            .advisors(advisor -> {
                advisor.param("user_id", 1L);
                advisor.param("api_key", "sk-test");
            })
            .stream()
            .chatResponse()
            .map(response -> response.getResult().getOutput().getText())
            .collectList()
            .block();

        // 内容 chunk 依次发出
        assertEquals(List.of("你", "好", ""), tokens);
        // 未走平台 ChatModel
        verify(chatModel, never()).stream(any(Prompt.class));
        // 最后一个 chunk 携带 usage，TokenUsageAdvisor 完成后置累计（自备 key 跳过前置校验）
        verify(tokenUsageService, never()).checkRemainingOrThrow(1L);
        verify(tokenUsageService).record(1L, 10, 5);
    }

    @Test
    void adviseStream_shouldPassThroughToChatModel_whenNoApiKey() throws Exception {
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

        chatClient.prompt()
            .system("system")
            .user("user")
            .stream()
            .chatResponse()
            .blockLast();

        verify(chatModel).stream(any(Prompt.class));
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    /** 同步读取 HttpRequest 的 body（BodyPublisher 无公开读取 API，需订阅收集）。 */
    private static byte[] readBody(HttpRequest request) {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        request.bodyPublisher().orElseThrow().subscribe(new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(java.nio.ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                baos.writeBytes(bytes);
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        return baos.toByteArray();
    }
}
