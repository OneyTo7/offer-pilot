package com.eyki.offerpilot.knowledge.domain;

import java.util.List;
import java.util.Optional;

/**
 * 知识库文档 Repository — 领域层接口。
 *
 * 定义持久化契约，不依赖任何基础设施框架。
 * 实现由 infrastructure 层提供。
 */
public interface KnowledgeDocumentRepository {

    /**
     * 保存文档（新增或更新）。
     * 新增时自动填充 ID 到实体。
     */
    KnowledgeDocument save(KnowledgeDocument document);

    /**
     * 按 ID 查询。
     */
    Optional<KnowledgeDocument> findById(Long id);

    /**
     * 按用户 + ID 查询（带用户隔离）。
     */
    Optional<KnowledgeDocument> findByUserIdAndId(Long userId, Long id);

    /**
     * 查询用户的所有文档，按创建时间倒序。
     */
    List<KnowledgeDocument> findByUserId(Long userId);

    /**
     * 按 ID 删除。
     */
    void deleteById(Long id);
}