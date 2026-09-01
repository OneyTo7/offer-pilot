package com.eyki.offerpilot.aicore.service.impl;

import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.common.exception.BusinessException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * AI service implementation. Wraps Spring AI's ChatClient to provide synchronous
 * and streaming LLM invocations. Uses DeepSeek via OpenAI-compatible API.
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private final ChatClient chatClient;

    public AiServiceImpl(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String chat(String systemPrompt, String userPrompt) {
        try {
            String response = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();

            if (response == null) {
                throw BusinessException.aiServiceError("AI 服务返回为空");
            }

            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public String chat(String systemPrompt, String userPrompt, Map<String, Object> context) {
        try {
            String response = chatClient.prompt().system(systemPrompt).user(userPrompt).call().content();

            if (response == null) {
                throw BusinessException.aiServiceError("AI 服务返回为空");
            }

            return response;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI 服务调用异常", e);
            throw BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage());
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt) {
        try {
            return chatClient.prompt().system(systemPrompt).user(userPrompt).stream().content().onErrorResume(e -> {
                log.error("AI 流式服务调用异常", e);
                return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            });
        } catch (Exception e) {
            log.error("AI 流式服务调用异常", e);
            return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
        }
    }

    @Override
    public Flux<String> chatStream(String systemPrompt, String userPrompt, Map<String, Object> context) {
        try {
            return chatClient.prompt().system(systemPrompt).user(userPrompt).stream().content().onErrorResume(e -> {
                log.error("AI 流式服务调用异常", e);
                return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
            });
        } catch (Exception e) {
            log.error("AI 流式服务调用异常", e);
            return Flux.error(BusinessException.aiServiceError("AI 服务调用失败: " + e.getMessage()));
        }
    }
}