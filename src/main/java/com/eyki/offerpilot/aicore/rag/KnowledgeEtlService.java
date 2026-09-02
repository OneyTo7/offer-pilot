package com.eyki.offerpilot.aicore.rag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * ETL pipeline for knowledge-base files, built on Spring AI's document readers and transformers:
 * <pre>
 * Extract   → MarkdownDocumentReader (.md) / TikaDocumentReader (.txt, PDF, DOCX, ...)
 * Transform → TokenTextSplitter (token-aware chunking, preserving reader structure)
 * Load      → VectorStore.add() (pgvector), chunks tagged with user_id / document_id
 * </pre>
 *
 * <p>Markdown files are parsed via {@link MarkdownDocumentReader} (heading-aware structural chunks);
 * everything else — pure TXT files included — goes through Apache Tika via
 * {@link TikaDocumentReader}. The pipeline is a no-op when no VectorStore is configured.</p>
 */
@Service
public class KnowledgeEtlService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeEtlService.class);

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final TokenTextSplitter textSplitter;

    public KnowledgeEtlService(ObjectProvider<VectorStore> vectorStoreProvider) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.textSplitter =
            TokenTextSplitter.builder().withChunkSize(CHUNK_SIZE).withMinChunkSizeChars(CHUNK_OVERLAP).build();
    }

    /**
     * Extract raw documents from an uploaded file, selecting the reader by file extension.
     * Markdown keeps its heading structure; TXT and other formats are parsed by Tika.
     */
    public List<Document> extract(Resource resource, String filename) {
        DocumentReader reader = selectReader(resource, filename);
        List<Document> documents = reader.get();

        // MarkdownDocumentReader 把标题放入 metadata["title"]，正文 content 不含题目；
        // 将标题拼接回 content，保证"题目+答案"在同一个分片（Tika 解析的文档无 title，原样返回）。
        documents = documents.stream().map(doc -> {
            Object title = doc.getMetadata().get("title");
            if (title == null || title.toString().isBlank()) {
                return doc;
            }
            return new Document(title + "\n\n" + doc.getText(), new HashMap<>(doc.getMetadata()));
        }).collect(Collectors.toList());

        log.info("ETL Extract 完成: filename={}, reader={}, documents={}", filename,
            reader.getClass().getSimpleName(), documents.size());
        return documents;
    }

    /**
     * Transform + Load: token-split the extracted documents and store the chunks in the vector store,
     * tagging each chunk with user_id (isolation) and document_id (lifecycle management).
     *
     * @return the stored chunks (empty when no VectorStore is available)
     */
    public List<Document> index(List<Document> documents, Long userId, String documentId,
        Map<String, Object> metadata) {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            log.warn("VectorStore 不可用，跳过知识库索引");
            return List.of();
        }
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = textSplitter.apply(documents);
        // chunk_index 记录分片在文档中的顺序（0..n），供"查看分片"按序展示
        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> meta = chunks.get(i).getMetadata();
            meta.put("user_id", userId.toString());
            meta.put("document_id", documentId);
            meta.put("chunk_index", i);
            if (metadata != null) {
                metadata.forEach(meta::putIfAbsent);
            }
        }

        vectorStore.add(chunks);
        log.info("ETL Load 完成: userId={}, documentId={}, chunks={}", userId, documentId, chunks.size());
        return chunks;
    }

    private DocumentReader selectReader(Resource resource, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            // 按标题层级分块，保留 Markdown 结构语义
            return new MarkdownDocumentReader(resource, MarkdownDocumentReaderConfig.builder().build());
        }
        // .txt (and PDF/DOCX/... fallback) via Apache Tika
        return new TikaDocumentReader(resource);
    }
}
