package com.eyki.offerpilot.aicore.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.eyki.offerpilot.aicore.memory.domain.ChatMemoryMessage;
import com.eyki.offerpilot.aicore.memory.repository.ChatMemoryRecordRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * MySQL-backed ChatMemory（对话记忆 + 持久化 + 窗口管理）。
 *
 * 并发安全：按 conversationId 粒度加锁，同一会话的 add/clear 串行化。 事务：delete + insert 在同一个事务内执行，保证原子性。
 */
@Slf4j
@Component
public class MysqlChatMemory implements ChatMemory {

    private static final int MAX_MESSAGES = 10;

    private final ChatMemoryRecordRepository repository;
    private final TransactionTemplate transactionTemplate;

    /** conversationId → userId 映射，用于标记消息归属用户 */
    private final Map<String, Long> conversationUsers = new ConcurrentHashMap<>();

    /** 按 conversationId 粒度的锁池，同一会话的 add/clear 互斥 */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public MysqlChatMemory(ChatMemoryRecordRepository repository, TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.transactionTemplate = transactionTemplate;
    }

    // ========== ChatMemory 接口实现 ==========

    @Override
    public void add(String conversationId, List<Message> messages) {
        // 按 conversationId 加锁，同一会话串行化
        synchronized (locks.computeIfAbsent(conversationId, k -> new Object())) {
            // 已有消息行
            List<ChatMemoryMessage> existingRows = loadMessages(conversationId);

            // 新消息转成行
            List<ChatMemoryMessage> newRows = toRows(conversationId, messages);

            // 合并并裁剪窗口
            List<ChatMemoryMessage> all = new ArrayList<>(existingRows);
            all.addAll(newRows);
            if (all.size() > MAX_MESSAGES) {
                all = all.subList(all.size() - MAX_MESSAGES, all.size());
            }

            // 事务内 delete + insert
            saveAllRows(conversationId, all);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        // 读操作不需要加锁，MySQL 的读一致性足够
        return loadMessages(conversationId).stream().map(this::toMessage).collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        synchronized (locks.computeIfAbsent(conversationId, k -> new Object())) {
            conversationUsers.remove(conversationId);
            repository.delete(new QueryWrapper<ChatMemoryMessage>().eq("conversation_id", conversationId));
            // 清理锁对象，避免内存泄漏
            locks.remove(conversationId);
        }
    }

    // ========== 用户关联方法 ==========

    public void registerConversation(String conversationId, Long userId) {
        if (conversationId != null && userId != null) {
            conversationUsers.put(conversationId, userId);
        }
    }

    public List<String> findConversationIdsByUserId(Long userId) {
        return repository.selectList(
                new QueryWrapper<ChatMemoryMessage>().eq("user_id", userId).select("DISTINCT conversation_id")
                    .orderByDesc("created_at")).stream().map(ChatMemoryMessage::getConversationId).distinct()
            .collect(Collectors.toList());
    }

    public List<ChatMemoryMessage> findMessagesByConversationId(String conversationId) {
        return repository.selectList(
            new QueryWrapper<ChatMemoryMessage>().eq("conversation_id", conversationId).orderByAsc("id"));
    }

    // ========== 内部方法 ==========

    private List<ChatMemoryMessage> loadMessages(String conversationId) {
        return repository.selectList(
            new QueryWrapper<ChatMemoryMessage>().eq("conversation_id", conversationId).orderByAsc("id"));
    }

    /**
     * 事务内执行 delete + insert，保证原子性。
     */
    private void saveAllRows(String conversationId, List<ChatMemoryMessage> rows) {
        transactionTemplate.executeWithoutResult(status -> {
            repository.delete(new QueryWrapper<ChatMemoryMessage>().eq("conversation_id", conversationId));

            if (!rows.isEmpty()) {
                repository.insert(rows);
            }
        });
    }

    private List<ChatMemoryMessage> toRows(String conversationId, List<Message> messages) {
        Long userId = conversationUsers.get(conversationId);
        LocalDateTime now = LocalDateTime.now();
        List<ChatMemoryMessage> rows = new ArrayList<>(messages.size());
        for (Message msg : messages) {
            ChatMemoryMessage row = new ChatMemoryMessage();
            row.setConversationId(conversationId);
            row.setUserId(userId);
            row.setMessageType(msg.getMessageType().name());
            row.setContent(msg.getText());
            row.setMetadata(
                msg.getMetadata() != null && !msg.getMetadata().isEmpty() ? cn.hutool.json.JSONUtil.toJsonStr(
                    msg.getMetadata()) : null);
            row.setCreatedAt(now);
            rows.add(row);
        }
        return rows;
    }

    private Message toMessage(ChatMemoryMessage row) {
        String content = row.getContent();
        MessageType type = MessageType.valueOf(row.getMessageType());
        return switch (type) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new SystemMessage(content);
            default -> {
                log.warn("未知消息类型，按 UserMessage 处理: {}", row.getMessageType());
                yield new UserMessage(content);
            }
        };
    }
}