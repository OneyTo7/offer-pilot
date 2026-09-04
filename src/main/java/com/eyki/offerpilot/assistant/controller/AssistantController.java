package com.eyki.offerpilot.assistant.controller;

import com.eyki.offerpilot.assistant.dto.ChatRequest;
import com.eyki.offerpilot.assistant.dto.ConversationVO;
import com.eyki.offerpilot.assistant.dto.MessageVO;
import com.eyki.offerpilot.assistant.service.AssistantService;
import com.eyki.offerpilot.common.model.ApiResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 小助手控制器。
 * <p>
 * 提供通用 AI 对话能力，支持联网搜索（通过 WebSearchTool）。
 * 对话使用 SSE 流式输出，提供类似 ChatGPT 的交互体验。
 */
@RestController
@RequestMapping("/api/v1/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantService assistantService;

    /**
     * 创建新对话。
     */
    @PostMapping("/conversations")
    public ApiResult<ConversationVO> createConversation() {
        ConversationVO conversation = assistantService.createConversation();
        return ApiResult.success(conversation);
    }

    /**
     * 获取用户的对话列表。
     */
    @GetMapping("/conversations")
    public ApiResult<List<ConversationVO>> listConversations() {
        List<ConversationVO> conversations = assistantService.listConversations();
        return ApiResult.success(conversations);
    }

    /**
     * 删除对话。
     */
    @DeleteMapping("/conversations/{id}")
    public ApiResult<?> deleteConversation(@PathVariable Long id) {
        assistantService.deleteConversation(id);
        return ApiResult.success("对话已删除");
    }

    /**
     * SSE 流式对话。
     * <p>
     * 发送消息后返回 SSE 流，事件类型：
     * <ul>
     *   <li><b>text</b> — AI 响应文本片段</li>
     *   <li><b>done</b> — 响应完成</li>
     *   <li><b>error</b> — 发生错误</li>
     * </ul>
     *
     * @param id      对话 ID
     * @param request 消息请求（含消息内容和联网搜索开关）
     * @return SSE 流
     */
    @PostMapping("/{id}/chat")
    public SseEmitter chat(@PathVariable Long id, @Valid @RequestBody ChatRequest request) {
        return assistantService.chat(id, request.getMessage(), request.isSearchEnabled());
    }

    /**
     * 获取对话历史消息。
     */
    @GetMapping("/{id}/messages")
    public ApiResult<List<MessageVO>> getMessages(@PathVariable Long id) {
        List<MessageVO> messages = assistantService.getMessages(id);
        return ApiResult.success(messages);
    }
}