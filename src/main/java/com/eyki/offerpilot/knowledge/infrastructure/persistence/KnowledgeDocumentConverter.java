package com.eyki.offerpilot.knowledge.infrastructure.persistence;

import com.eyki.offerpilot.knowledge.domain.ContentType;
import com.eyki.offerpilot.knowledge.domain.DocumentStatus;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocument;
import org.springframework.stereotype.Component;

/**
 * 持久化对象 ⇔ 领域对象 双向转换器。
 *
 * 处理枚举 ↔ 数据库字面量的映射，确保两层模型互不污染。
 */
@Component
public class KnowledgeDocumentConverter {

    public KnowledgeDocument toDomain(KnowledgeDocumentPO po) {
        return KnowledgeDocument.restore(po.getId(), po.getUserId(), po.getTitle(), po.getContent(),
            ContentType.fromValue(po.getContentType()), po.getFileUrl(),
            po.getChunkCount() != null ? po.getChunkCount() : 0, DocumentStatus.fromCode(po.getStatus()),
            po.getFailReason(), po.getCreatedAt(), po.getUpdatedAt());
    }

    public KnowledgeDocumentPO toPO(KnowledgeDocument domain) {
        KnowledgeDocumentPO po = new KnowledgeDocumentPO();
        po.setId(domain.getId());
        po.setUserId(domain.getUserId());
        po.setTitle(domain.getTitle());
        po.setContent(domain.getContent());
        po.setContentType(domain.getContentType().getValue());
        po.setFileUrl(domain.getFileUrl());
        po.setChunkCount(domain.getChunkCount());
        po.setStatus(domain.getStatus().getCode());
        po.setFailReason(domain.getFailReason());
        po.setCreatedAt(domain.getCreatedAt());
        po.setUpdatedAt(domain.getUpdatedAt());
        return po;
    }
}