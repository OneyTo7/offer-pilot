package com.eyki.offerpilot.knowledge.application.dto;

import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * 知识文档分片返回 VO。
 *
 * <p>分片存储在 pgvector 的 {@code vector_store} 表中，此处按文档 ID + 用户 ID 查询后透出。
 * {@code index} 为分片在文档中的顺序（新数据来自 metadata 的 {@code chunk_index}，旧数据可能为 null）。</p>
 */
@Data
@Builder
public class KnowledgeChunkVO {

    /** pgvector 分片行 ID（uuid） */
    private String id;

    /** 分片顺序号（0 起；旧数据无 chunk_index 时为 null） */
    private Integer index;

    /** 分片内容 */
    private String content;

    /** 分片元数据（user_id / document_id / title / source / chunk_index 等） */
    private Map<String, Object> metadata;
}
