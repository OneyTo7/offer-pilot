package com.eyki.offerpilot.knowledge.application;

import com.eyki.offerpilot.aicore.rag.KnowledgeEtlService;
import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import com.eyki.offerpilot.knowledge.application.assembler.KnowledgeAssembler;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeChunkVO;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeDocumentDetailVO;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeDocumentVO;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeSearchRequest;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeSearchResult;
import com.eyki.offerpilot.knowledge.application.dto.KnowledgeUploadRequest;
import com.eyki.offerpilot.knowledge.domain.ContentType;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocument;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocumentRepository;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 知识库 — 应用服务。
 *
 * 职责：编排领域对象和基础设施，控制事务边界，DTO 转换。 不包含业务逻辑（业务逻辑在 KnowledgeDocument 领域实体中）。
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

    /** 知识库文件大小上限：5MB */
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;

    private final KnowledgeDocumentRepository repository;
    private final KnowledgeAssembler assembler;
    private final AuthService authService;
    private final RagService ragService;
    private final KnowledgeEtlService etlService;

    /**
     * 创建知识文档。
     *
     * 流程：创建领域实体 → 持久化 → 索引到 pgvector → 更新索引状态。 索引失败不阻断流程，实体状态标记为 FAILED 供前端展示。
     */
    @Transactional
    public KnowledgeDocumentVO create(KnowledgeUploadRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();
        String scope = request.getScope();

        // 如果是系统级文档，需要管理员权限
        if ("system".equals(scope)) {
            checkAdmin();
        }

        // 1. 领域工厂创建实体（执行业务校验）
        ContentType contentType = ContentType.fromValue(request.getContentType());
        KnowledgeDocument doc = KnowledgeDocument.create(userId, request.getTitle(), request.getContent(), contentType, scope);

        // 2. 持久化
        repository.save(doc);

        // 3. 索引到 pgvector
        try {
            Map<String, Object> metadata =
                Map.of(META_DOCUMENT_ID, doc.getId().toString(), META_TITLE, doc.getTitle(), META_USER_ID,
                    userId.toString(), META_SOURCE, "knowledge_base",
                    "scope", doc.getScope());
            ragService.indexDocument(doc.getContent(), userId, null, metadata);

            // 4. 标记索引完成
            doc.markIndexed();
        } catch (Exception e) {
            log.error("知识文档索引失败: docId={}, title={}", doc.getId(), doc.getTitle(), e);
            doc.markFailed(e.getMessage() != null ? e.getMessage() : "索引异常");
        }

        // 5. 更新持久化状态
        repository.save(doc);

        log.info("知识文档创建成功: docId={}, userId={}, title={}, scope={}, status={}", doc.getId(), userId, doc.getTitle(),
            doc.getScope(), doc.getStatus().getDescription());
        return assembler.toVO(doc);
    }

    /**
     * 上传知识库文件（Markdown / 纯 TXT）。
     *
     * ETL 流程：文件 → DocumentReader 解析（Markdown/Tika）→ TokenTextSplitter 分块 → pgvector 索引，
     * 解析出的全文同时存入 MySQL 供详情展示。索引失败不阻断流程，状态标记 FAILED 供前端展示。
     */
    @Transactional
    public KnowledgeDocumentVO upload(MultipartFile file, String title, String scope) {
        Long userId = authService.getCurrentUserEntity().getId();

        // 如果是系统级文档，需要管理员权限
        if ("system".equals(scope)) {
            checkAdmin();
        }

        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String lower = filename.toLowerCase(Locale.ROOT);

        // 校验：仅支持 Markdown 和纯 TXT
        boolean supported = lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt");
        if (!supported) {
            throw BusinessException.badRequest("仅支持 Markdown (.md) 和纯文本 (.txt) 文件");
        }
        if (file.isEmpty()) {
            throw BusinessException.badRequest("文件内容为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw BusinessException.badRequest("文件大小不能超过 5MB");
        }

        // 1. ETL Extract：按扩展名选择 DocumentReader 解析文件
        List<Document> parsedDocs;
        try {
            Resource resource = new InputStreamResource(new ByteArrayInputStream(file.getBytes())) {
                @Override
                public String getFilename() {
                    return filename;
                }
            };
            parsedDocs = etlService.extract(resource, filename);
            if (parsedDocs.isEmpty()) {
                throw BusinessException.of(ErrorCode.DOCUMENT_PARSE_ERROR, "文件内容为空，无法解析");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("知识库文件解析失败: filename={}", filename, e);
            throw BusinessException.of(ErrorCode.DOCUMENT_PARSE_ERROR, "文件解析失败: " + e.getMessage());
        }

        // 2. 领域实体创建 + 持久化（content 为解析后的全文）
        String docTitle = title != null && !title.isBlank() ? title : filename;
        String content = parsedDocs.stream().map(Document::getText).collect(Collectors.joining("\n\n"));
        KnowledgeDocument doc = KnowledgeDocument.create(userId, docTitle, content, ContentType.FILE, scope != null ? scope : "user");
        repository.save(doc);

        // 3. ETL Transform + Load：分块索引到 pgvector（保留 reader 结构分块）
        try {
            Map<String, Object> metadata =
                Map.of(META_TITLE, doc.getTitle(), META_SOURCE, "knowledge_base",
                    "scope", doc.getScope());
            etlService.index(parsedDocs, userId, doc.getId().toString(), metadata);
            doc.markIndexed();
        } catch (Exception e) {
            log.error("知识库文件索引失败: docId={}, filename={}", doc.getId(), filename, e);
            doc.markFailed(e.getMessage() != null ? e.getMessage() : "索引异常");
        }

        // 4. 更新索引状态
        repository.save(doc);

        log.info("知识库文件上传成功: docId={}, userId={}, filename={}, status={}", doc.getId(), userId, filename,
            doc.getStatus().getDescription());
        return assembler.toVO(doc);
    }

    /**
     * 获取用户的知识文档列表（仅用户级）。
     */
    public List<KnowledgeDocumentVO> listMyDocuments() {
        Long userId = authService.getCurrentUserEntity().getId();
        return repository.findByUserIdAndScope(userId, "user").stream().map(assembler::toVO).collect(Collectors.toList());
    }

    /**
     * 获取系统级知识文档列表（管理员）。
     */
    public List<KnowledgeDocumentVO> listSystemDocuments() {
        checkAdmin();
        return repository.findByScope("system").stream().map(assembler::toVO).collect(Collectors.toList());
    }

    /**
     * 获取知识文档详情（含全文内容）。
     */
    public KnowledgeDocumentDetailVO getDetail(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        KnowledgeDocument doc =
            repository.findByUserIdAndId(userId, id).orElseThrow(() -> BusinessException.notFound("知识文档不存在"));
        return assembler.toDetailVO(doc);
    }

    /**
     * 查看知识文档的全部分片（来自 pgvector vector_store）。
     *
     * 先校验文档归属（防止越权），再按 user_id + document_id 查询分片。
     */
    public List<KnowledgeChunkVO> listChunks(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        KnowledgeDocument doc =
            repository.findByUserIdAndId(userId, id).orElseThrow(() -> BusinessException.notFound("知识文档不存在"));

        return ragService.listChunks(doc.getId().toString(), userId).stream().map(assembler::toChunkVO)
            .collect(Collectors.toList());
    }

    /**
     * 删除知识文档（MySQL 记录 + pgvector 向量）。
     */
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        KnowledgeDocument doc =
            repository.findByUserIdAndId(userId, id).orElseThrow(() -> BusinessException.notFound("知识文档不存在"));

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

        return results.stream().map(assembler::toSearchResult).collect(Collectors.toList());
    }

    /**
     * 校验当前用户是否为管理员。
     */
    private void checkAdmin() {
        com.eyki.offerpilot.auth.domain.User user = authService.getCurrentUserEntity();
        if (!"admin".equals(user.getRole())) {
            throw BusinessException.of(ErrorCode.FORBIDDEN, "仅管理员可执行此操作");
        }
    }
}