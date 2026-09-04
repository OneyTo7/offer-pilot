package com.eyki.offerpilot.assistant.service;

import com.eyki.offerpilot.aicore.memory.PgChatMemory;
import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.aicore.tool.WebSearchTool;
import com.eyki.offerpilot.assistant.domain.AssistantConversation;
import com.eyki.offerpilot.assistant.dto.ConversationVO;
import com.eyki.offerpilot.assistant.dto.MessageVO;
import com.eyki.offerpilot.assistant.repository.AssistantConversationRepository;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 小助手服务。
 * <p>
 * 提供通用 AI 对话能力，支持联网搜索（通过 WebSearchTool）。
 * 每个对话使用独立的 conversation_id，通过 MessageChatMemoryAdvisor 自动注入历史上下文。
 * 联网搜索由用户通过前端 toggle 开关控制，默认关闭以节省 API 额度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 分钟

    /**
     * 系统提示词（无联网搜索，AI 不主动搜索）。
     * 当 searchEnabled 为 false 时使用此提示词。
     */
    private static final String SYSTEM_PROMPT = """
        你是面壁 OfferPilot 平台的 AI 小助手，一个专业的求职辅助 AI。
        你的主要能力包括：
        1. 回答用户关于求职、简历、面试、技术栈等通用问题
        2. 帮助用户了解 OfferPilot 平台的功能和使用方法
        3. 根据你的知识回答用户的问题

        注意：你当前没有联网搜索工具可用。对于需要实时信息的问题（如新闻、最新技术动态、产品发布、天气等），
        请如实告诉用户："联网搜索未开启，请打开输入框上方的「联网搜索」开关后重新提问。"
        绝对不要假装搜索、不要描述搜索过程、不要编造搜索结果。
        """;

    private final AssistantConversationRepository conversationRepository;
    private final ChatClient chatClient;
    private final PgChatMemory chatMemoryStore;
    private final Executor sseTaskExecutor;
    private final AuthService authService;
    private final WebSearchTool webSearchTool;
    private final RagService ragService;

    /**
     * 创建新对话。
     */
    @Transactional
    public ConversationVO createConversation() {
        Long userId = authService.getCurrentUserEntity().getId();
        AssistantConversation conversation = new AssistantConversation();
        conversation.setUserId(userId);
        conversation.setTitle("新对话");
        conversation.setStatus("ACTIVE");
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.insert(conversation);

        // 注册对话到 PgChatMemory，关联用户 ID
        chatMemoryStore.registerConversation(conversation.getId().toString(), userId);

        log.info("Assistant conversation created | id={} | userId={}", conversation.getId(), userId);
        return toVO(conversation);
    }

    /**
     * 获取用户的对话列表。
     */
    public List<ConversationVO> listConversations() {
        Long userId = authService.getCurrentUserEntity().getId();
        return conversationRepository.findByUserId(userId).stream()
            .map(this::toVO)
            .toList();
    }

    /**
     * 删除对话（软删除）。
     */
    @Transactional
    public void deleteConversation(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        AssistantConversation conversation = conversationRepository.selectById(id);
        BusinessException.checkOwnership(conversation != null && conversation.getUserId().equals(userId),
            () -> BusinessException.of(ErrorCode.NOT_FOUND, "对话不存在"));
        conversation.setStatus("DELETED");
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.updateById(conversation);

        // 清除对话记忆
        chatMemoryStore.clear(conversation.getId().toString());
        log.info("Assistant conversation deleted | id={} | userId={}", id, userId);
    }

    /**
     * 获取对话历史消息。
     */
    public List<MessageVO> getMessages(Long conversationId) {
        Long userId = authService.getCurrentUserEntity().getId();
        AssistantConversation conversation = conversationRepository.selectById(conversationId);
        BusinessException.checkOwnership(conversation != null && conversation.getUserId().equals(userId),
            () -> BusinessException.of(ErrorCode.NOT_FOUND, "对话不存在"));

        return chatMemoryStore.findMessagesByConversationId(conversationId.toString()).stream()
            .map(row -> {
                MessageVO vo = new MessageVO();
                vo.setRole(row.getMessageType().equals("USER") ? "user" : "assistant");
                vo.setContent(row.getContent());
                vo.setCreatedAt(row.getCreatedAt());
                return vo;
            })
            .toList();
    }

    /**
     * SSE 流式对话。
     * <p>
     * 用户消息通过 chatClient 发送，AI 响应通过 SSE 流式推送。
     * 当 searchEnabled 为 true 时，注入 WebSearchTool，AI 可自主决定是否联网搜索。
     * 默认为 false，以节省 API 额度。
     *
     * @param conversationId 对话 ID
     * @param message        用户消息
     * @param searchEnabled  是否开启联网搜索
     * @return SSE 流
     */
    public SseEmitter chat(Long conversationId, String message, boolean searchEnabled) {
        // 获取当前用户信息（同步操作，在 async 之前捕获）
        User user = authService.getCurrentUserEntity();
        Long userId = user.getId();
        log.info("Assistant chat request | convId={} | userId={} | searchEnabled={}", conversationId, userId, searchEnabled);

        log.info("Assistant chat request | convId={} | userId={} | searchEnabled={} | message={}", conversationId, userId, searchEnabled, message.length() > 100 ? message.substring(0, 100) + "..." : message);

        // 校验对话归属
        AssistantConversation conversation = conversationRepository.selectById(conversationId);
        BusinessException.checkOwnership(conversation != null && conversation.getUserId().equals(userId),
            () -> BusinessException.of(ErrorCode.NOT_FOUND, "对话不存在"));

        // 更新对话标题（首条消息作为标题）
        if ("新对话".equals(conversation.getTitle()) && message.length() <= 200) {
            conversation.setTitle(message.length() > 30 ? message.substring(0, 30) + "..." : message);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.updateById(conversation);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        String convId = conversationId.toString();

        sseTaskExecutor.execute(() -> {
            try {
                // 构建系统提示词 + 可选的联网搜索结果
                StringBuilder systemBuilder = new StringBuilder(SYSTEM_PROMPT);
                String userMessage = message;

                if (searchEnabled) {
                    log.info("Assistant SSE | performing web search for query: {}", message);
                    String searchResult = webSearchTool.search(message);
                    if (searchResult != null && !searchResult.isBlank()
                        && !searchResult.startsWith("搜索") && !searchResult.startsWith("未找到")) {
                        systemBuilder.append("\n\n====== 以下为联网搜索结果 ======\n");
                        systemBuilder.append(searchResult);
                        systemBuilder.append("\n====== 以上为联网搜索结果 ======\n");
                        systemBuilder.append("请基于以上搜索结果回答用户的问题。如果搜索结果与问题无关，请忽略搜索结果。\n");
                        systemBuilder.append("注意：不要提及搜索过程，直接给出答案。");
                        userMessage = "[联网搜索已开启] " + message;
                    } else {
                        log.warn("Assistant SSE | web search returned no results: {}", searchResult);
                        userMessage = "[联网搜索已开启，但未找到相关结果] " + message;
                    }
                } else {
                    userMessage = message;
                }

                String systemPrompt = systemBuilder.toString();
                log.info("Assistant SSE start | systemPromptLength={} | searchEnabled={}",
                    systemPrompt.length(), searchEnabled);

                var promptBuilder = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userMessage)
                    .advisors(a -> {
                        a.param("user_id", userId);
                        a.param("chat_memory_conversation_id", convId);
                        // RAG 过滤：检索用户自己的知识库 + 系统级知识库
                        a.param("vector_store_filter_expression", ragService.buildAssistantFilter(userId));
                        // 用户自备 API Key 时，传递给 TokenUsageAdvisor 和 ApiKeyRoutingAdvisor
                        if (user.getApiKey() != null && !user.getApiKey().isBlank()) {
                            a.param("api_key", user.getApiKey());
                        }
                        if (user.getApiBaseUrl() != null && !user.getApiBaseUrl().isBlank()) {
                            a.param("api_base_url", user.getApiBaseUrl());
                        }
                        if (user.getApiModel() != null && !user.getApiModel().isBlank()) {
                            a.param("api_model", user.getApiModel());
                        }
                    });

                promptBuilder.stream()
                    .chatResponse()
                    .doOnNext(response -> {
                        if (response.getResult() == null
                            || response.getResult().getOutput() == null) {
                            return;
                        }
                        String content = response.getResult().getOutput().getText();
                        if (content == null || content.isEmpty()) {
                            return;
                        }
                        try {
                            emitter.send(SseEmitter.event()
                                .name("text")
                                .data(content));
                        } catch (java.io.IOException e) {
                            log.warn("Failed to send SSE event, client may have disconnected | convId={}", convId);
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            emitter.send(SseEmitter.event()
                                .name("done")
                                .data(""));
                            emitter.complete();
                        } catch (java.io.IOException e) {
                            log.warn("Failed to send SSE done event | convId={}", convId);
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("Assistant chat error | convId={} | searchEnabled={}", convId, searchEnabled, error);
                        if (error instanceof BusinessException be
                            && be.getCode() == ErrorCode.API_KEY_INSUFFICIENT_BALANCE) {
                            try {
                                emitter.send(SseEmitter.event().name("error").data(be.getMessage()));
                                emitter.complete();
                            } catch (Exception ex) {
                                emitter.completeWithError(ex);
                            }
                        } else {
                            emitter.completeWithError(error);
                        }
                    })
                    .subscribe();

            } catch (Exception e) {
                log.error("Assistant chat failed | convId={}", convId, e);
                try {
                    String errorMessage;
                    if (e instanceof BusinessException be
                        && be.getCode() == ErrorCode.API_KEY_INSUFFICIENT_BALANCE) {
                        errorMessage = be.getMessage();
                    } else {
                        errorMessage = "抱歉，我遇到了一些问题，请稍后再试。";
                    }
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data(errorMessage));
                    emitter.complete();
                } catch (Exception ex) {
                    // ignore
                }
            }
        });

        return emitter;
    }

    private ConversationVO toVO(AssistantConversation conversation) {
        ConversationVO vo = new ConversationVO();
        vo.setId(conversation.getId());
        vo.setTitle(conversation.getTitle());
        vo.setCreatedAt(conversation.getCreatedAt());
        vo.setUpdatedAt(conversation.getUpdatedAt());
        return vo;
    }
}