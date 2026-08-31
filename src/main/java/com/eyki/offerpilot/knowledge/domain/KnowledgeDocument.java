package com.eyki.offerpilot.knowledge.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 知识库文档 — 领域聚合根。
 *
 * 每个文档代表用户上传的一份知识内容，经分片处理后存入向量库用于 RAG 检索。
 * 封装了文档创建、状态转换、内容校验等核心业务规则。
 *
 * ⚠️ 本类为纯领域对象，不依赖任何框架注解（无 @TableName、无 @Data）。
 */
public class KnowledgeDocument {

    private static final int CHUNK_SIZE = 500;

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private ContentType contentType;
    private String fileUrl;
    private int chunkCount;
    private DocumentStatus status;
    private String failReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ========== 工厂方法 ==========

    /**
     * 创建新的知识文档（新实体，id=null，status=INDEXING）。
     */
    public static KnowledgeDocument create(Long userId, String title, String content, ContentType contentType) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.userId = userId;
        doc.title = title.trim();
        doc.content = content;
        doc.contentType = contentType != null ? contentType : ContentType.TEXT;
        doc.chunkCount = 0;
        doc.status = DocumentStatus.INDEXING;
        doc.createdAt = LocalDateTime.now();
        doc.updatedAt = LocalDateTime.now();
        return doc;
    }

    /**
     * 从持久化存储重建文档（供 Repository 从数据库加载时使用）。
     * 与 {@link #create} 不同，该方法不执行创建时校验，直接还原全部字段。
     */
    public static KnowledgeDocument restore(Long id, Long userId, String title, String content,
                                            ContentType contentType, String fileUrl, int chunkCount,
                                            DocumentStatus status, String failReason,
                                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(contentType, "contentType must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.id = id;
        doc.userId = userId;
        doc.title = title;
        doc.content = content;
        doc.contentType = contentType;
        doc.fileUrl = fileUrl;
        doc.chunkCount = chunkCount;
        doc.status = status;
        doc.failReason = failReason;
        doc.createdAt = createdAt;
        doc.updatedAt = updatedAt;
        return doc;
    }

    // ========== 业务行为 ==========

    /**
     * 标记索引完成。
     * 仅当当前状态为 INDEXING 时允许调用。
     */
    public void markIndexed() {
        ensureStatus(DocumentStatus.INDEXING, "mark as indexed");
        this.status = DocumentStatus.COMPLETED;
        this.chunkCount = estimateChunkCount();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 标记索引失败。
     * 仅当当前状态为 INDEXING 时允许调用。
     */
    public void markFailed(String reason) {
        ensureStatus(DocumentStatus.INDEXING, "mark as failed");
        this.status = DocumentStatus.FAILED;
        this.failReason = reason;
        this.updatedAt = LocalDateTime.now();
    }

    // ========== 查询方法 ==========

    public boolean isCompleted() {
        return status == DocumentStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == DocumentStatus.FAILED;
    }

    public boolean isIndexing() {
        return status == DocumentStatus.INDEXING;
    }

    // ========== 基础设施回调（仅 Repository 使用） ==========

    /**
     * 持久化后设置数据库自增 ID。
     * 仅允许设置一次（id 为 null 时）。
     */
    public void onPersisted(Long id) {
        if (this.id != null) {
            throw new IllegalStateException("Cannot change ID once set");
        }
        this.id = id;
    }

    // ========== Getters ==========

    public Long getId()                           { return id; }
    public Long getUserId()                       { return userId; }
    public String getTitle()                      { return title; }
    public String getContent()                    { return content; }
    public ContentType getContentType()           { return contentType; }
    public String getFileUrl()                    { return fileUrl; }
    public int getChunkCount()                    { return chunkCount; }
    public DocumentStatus getStatus()             { return status; }
    public String getFailReason()                 { return failReason; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public LocalDateTime getUpdatedAt()           { return updatedAt; }

    // ========== 内部方法 ==========

    private void ensureStatus(DocumentStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + ": current status is " + this.status.getDescription()
                            + ", expected " + expected.getDescription());
        }
    }

    private int estimateChunkCount() {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil((double) content.length() / CHUNK_SIZE);
    }

    // ========== equals / hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KnowledgeDocument that = (KnowledgeDocument) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "KnowledgeDocument{" +
                "id=" + id +
                ", userId=" + userId +
                ", title='" + title + '\'' +
                ", status=" + status +
                '}';
    }
}