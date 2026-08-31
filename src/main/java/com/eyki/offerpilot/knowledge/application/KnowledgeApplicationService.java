package com.eyki.offerpilot.knowledge.application;

import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.knowledge.application.assembler.KnowledgeAssembler;
import com.eyki.offerpilot.knowledge.application.dto.*;
import com.eyki.offerpilot.knowledge.domain.ContentType;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocument;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识库 — 应用服务。
 *
 * 职责：编排领域对象和基础设施，控制事务边界，DTO 转换。
 * 不包含业务逻辑（业务逻辑在 KnowledgeDocument 领域实体中）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeApplicationService {

    /** pgvector 元数据 key */
    private static final String META_DOCUMENT_ID = "document_id";
    private static final String META_TITLE = "title";
    private static final String META_USER_ID = "user_id";
    private static final String META_SOURCE = "source";

    private final KnowledgeDocumentRepository repository;
    private final KnowledgeAssembler assembler;
    private final AuthService authService;
    private final RagService ragService;

    /**
     * 创建知识文档。
     *
     * 流程：创建领域实体 → 持久化 → 索引到 pgvector → 更新索引状态。
     * 索引失败不阻断流程，实体状态标记为 FAILED 供前端展示。
     */
    @Transactional
    public KnowledgeDocumentVO create(KnowledgeUploadRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();

        // 1. 领域工厂创建实体（执行业务校验）
        ContentType contentType = ContentType.fromValue(request.getContentType());
        KnowledgeDocument doc = KnowledgeDocument.create(
                userId, request.getTitle(), request.getContent(), contentType);

        // 2. 持久化
        repository.save(doc);

        // 3. 索引到 pgvector
        try {
            Map<String, Object> metadata = Map.of(
                    META_DOCUMENT_ID, doc.getId().toString(),
                    META_TITLE, doc.getTitle(),
                    META_USER_ID, userId.toString(),
                    META_SOURCE, "knowledge_base");
            ragService.indexDocument(doc.getContent(), userId, null, metadata);

            // 4. 标记索引完成
            doc.markIndexed();
        } catch (Exception e) {
            log.error("知识文档索引失败: docId={}, title={}", doc.getId(), doc.getTitle(), e);
            doc.markFailed(e.getMessage() != null ? e.getMessage() : "索引异常");
        }

        // 5. 更新持久化状态
        repository.save(doc);

        log.info("知识文档创建成功: docId={}, userId={}, title={}, status={}",
                doc.getId(), userId, doc.getTitle(), doc.getStatus().getDescription());
        return assembler.toVO(doc);
    }

    /**
     * 获取用户的知识文档列表。
     */
    public List<KnowledgeDocumentVO> listMyDocuments() {
        Long userId = authService.getCurrentUserEntity().getId();
        return repository.findByUserId(userId).stream()
                .map(assembler::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取知识文档详情（含全文内容）。
     */
    public KnowledgeDocumentDetailVO getDetail(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        KnowledgeDocument doc = repository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> BusinessException.notFound("知识文档不存在"));
        return assembler.toDetailVO(doc);
    }

    /**
     * 删除知识文档（MySQL 记录 + pgvector 向量）。
     */
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        KnowledgeDocument doc = repository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> BusinessException.notFound("知识文档不存在"));

        // 从 pgvector 删除该文档的所有分片
        ragService.deleteByDocumentId(doc.getId().toString(), userId);

        // 删除 MySQL 记录
        repository.deleteById(id);

        log.info("知识文档删除成功: docId={}, userId={}, title={}", id, userId, doc.getTitle());
    }

    /**
     * 语义搜索用户的知识库。
     */
    public List<KnowledgeSearchResult> search(KnowledgeSearchRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();
        List<Document> results = ragService.search(request.getQuery(), userId, request.getTopK());

        return results.stream()
                .map(assembler::toSearchResult)
                .collect(Collectors.toList());
    }
}