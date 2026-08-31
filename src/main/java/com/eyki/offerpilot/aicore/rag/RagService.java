package com.eyki.offerpilot.aicore.rag;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * RAG service for indexing, searching, and deleting documents in the pgvector store. All documents are scoped by
 * user_id for data isolation. Designed to work without a VectorStore when pgvector is not configured.
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;

    public RagService(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.textSplitter =
            TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE).withMinChunkSizeChars(CHUNK_OVERLAP).build();
    }

    /**
     * Index a document for RAG retrieval.
     */
    public void indexDocument(String text, Long userId, String resumeId, Map<String, Object> metadata) {
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，跳过文档索引");
            return;
        }
        List<Document> chunks = textSplitter.apply(List.of(new Document(text, metadata)));

        // Add user_id to each chunk's metadata for filtering
        chunks.forEach(chunk -> {
            chunk.getMetadata().put("user_id", userId.toString());
            if (resumeId != null) {
                chunk.getMetadata().put("resume_id", resumeId);
            }
        });

        vectorStore.add(chunks);
        log.info("文档索引成功: userId={}, chunks={}, resumeId={}", userId, chunks.size(), resumeId);
    }

    /**
     * Search for relevant documents for a given user and query.
     */
    public List<Document> search(String query, Long userId, int topK) {
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，返回空结果");
            return List.of();
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.eq("user_id", userId.toString()).build();

        SearchRequest searchRequest = SearchRequest.builder().query(query).topK(topK > 0 ? topK : DEFAULT_TOP_K)
            .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD).filterExpression(filter).build();

        List<Document> results = vectorStore.similaritySearch(searchRequest);
        log.debug("RAG 检索完成: userId={}, query={}, results={}", userId, query, results.size());
        return results;
    }

    /**
     * Search with default top-K.
     */
    public List<Document> search(String query, Long userId) {
        return search(query, userId, DEFAULT_TOP_K);
    }

    /**
     * Delete all documents for a given user.
     */
    public void deleteByUserId(Long userId) {
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，跳过删除操作");
            return;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter = builder.eq("user_id", userId.toString()).build();
        vectorStore.delete(filter);
        log.info("用户文档删除成功: userId={}", userId);
    }

    /**
     * Delete documents for a specific resume.
     */
    public void deleteByResumeId(Long userId, String resumeId) {
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，跳过删除操作");
            return;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter =
            builder.and(builder.eq("user_id", userId.toString()), builder.eq("resume_id", resumeId)).build();
        vectorStore.delete(filter);
        log.info("简历文档删除成功: userId={}, resumeId={}", userId, resumeId);
    }

    /**
     * Delete all chunks for a specific knowledge document.
     */
    public void deleteByDocumentId(String documentId, Long userId) {
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，跳过删除操作");
            return;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression filter =
            builder.and(builder.eq("user_id", userId.toString()), builder.eq("document_id", documentId)).build();
        vectorStore.delete(filter);
        log.info("知识文档向量删除成功: userId={}, documentId={}", userId, documentId);
    }
}