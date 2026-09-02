package com.eyki.offerpilot.aicore.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.jdbc.core.JdbcTemplate;
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

    /** pgvector 表名，需与 PgVectorStore 默认值一致（schemaName=public, tableName=vector_store） */
    private static final String VECTOR_STORE_TABLE = "public.vector_store";

    /** metadata 中记录分片顺序的 key（写入时按 0..n 递增；旧数据可能缺失） */
    private static final String META_CHUNK_INDEX = "chunk_index";

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RagService(ObjectProvider<VectorStore> vectorStoreProvider, JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper) {
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.textSplitter =
            TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE).withMinChunkSizeChars(CHUNK_OVERLAP).build();
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
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

        // Add user_id to each chunk's metadata for filtering; chunk_index preserves document order
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = chunks.get(i).getMetadata();
            meta.put("user_id", userId.toString());
            meta.put(META_CHUNK_INDEX, i);
            if (resumeId != null) {
                meta.put("resume_id", resumeId);
            }
        }

        vectorStore.add(chunks);
        log.info("文档索引成功: userId={}, chunks={}, resumeId={}", userId, chunks.size(), resumeId);
    }

    /**
     * List all chunks of a knowledge document (user-scoped), in chunk order.
     *
     * <p>Queries {@code vector_store} directly since the VectorStore API offers no
     * "list by metadata" operation. Old chunks without a {@code chunk_index} sort last.</p>
     *
     * @return the chunks, each carrying its pgvector row id in {@code metadata["vector_store_id"]}
     */
    public List<Document> listChunks(String documentId, Long userId) {
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，跳过分片查询");
            return List.of();
        }
        String sql = "SELECT id, content, metadata FROM " + VECTOR_STORE_TABLE
            + " WHERE metadata->>'user_id' = ? AND metadata->>'document_id' = ?"
            + " ORDER BY (metadata->>'chunk_index')::int NULLS LAST, id";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Document doc = new Document(rs.getString("content"), parseMetadata(rs.getString("metadata")));
            doc.getMetadata().put("vector_store_id", rs.getString("id"));
            return doc;
        }, userId.toString(), documentId);
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析 vector_store metadata 失败，忽略: {}", e.getMessage());
            return Map.of();
        }
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
     * Build a user-scoped filter expression, e.g. for the {@code vector_store_filter_expression}
     * context param consumed by RetrievalAugmentationAdvisor (platform-key ChatClient path).
     */
    public Filter.Expression buildUserFilter(Long userId) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        return builder.eq("user_id", userId.toString()).build();
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