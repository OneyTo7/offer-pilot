package com.eyki.offerpilot.knowledge.application.assembler;

import com.eyki.offerpilot.knowledge.application.dto.KnowledgeDocumentDetailVO;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeDocumentVO;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeSearchResult;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocument;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 领域对象 ⇔ DTO 转换器。
 *
 * 职责清晰：只做字段映射，不包含业务逻辑。
 */
@Component
public class KnowledgeAssembler {

    public KnowledgeDocumentVO toVO(KnowledgeDocument doc) {
        return KnowledgeDocumentVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .contentType(doc.getContentType().getValue())
                .fileUrl(doc.getFileUrl())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus().getCode())
                .statusDesc(doc.getStatus().getDescription())
                .failReason(doc.getFailReason())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    public KnowledgeDocumentDetailVO toDetailVO(KnowledgeDocument doc) {
        return KnowledgeDocumentDetailVO.builder()
                .id(doc.getId())
                .title(doc.getTitle())
                .content(doc.getContent())
                .contentType(doc.getContentType().getValue())
                .fileUrl(doc.getFileUrl())
                .chunkCount(doc.getChunkCount())
                .status(doc.getStatus().getCode())
                .statusDesc(doc.getStatus().getDescription())
                .failReason(doc.getFailReason())
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    public KnowledgeSearchResult toSearchResult(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        Long documentId = meta.get("document_id") != null
                ? Long.valueOf(meta.get("document_id").toString()) : null;
        String title = meta.get("title") != null
                ? meta.get("title").toString() : null;
        Double score = meta.get("distance") != null
                ? (Double) meta.get("distance") : null;

        return KnowledgeSearchResult.builder()
                .documentId(documentId)
                .title(title)
                .content(doc.getText())
                .score(score)
                .build();
    }
}