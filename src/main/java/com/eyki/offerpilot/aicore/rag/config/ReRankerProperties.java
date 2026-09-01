package com.eyki.offerpilot.aicore.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the BGE cross-encoder re-ranker.
 *
 * <p>Bound from {@code spring.ai.rag.reranker.*}; model URIs default to the
 * Xenova/bge-reranker-base ONNX export on the hf-mirror.com mirror and can be
 * overridden via environment variables (e.g. {@code RERANKER_MODEL_URI}) for
 * self-hosted mirrors.</p>
 */
@ConfigurationProperties(prefix = "spring.ai.rag.reranker")
public class ReRankerProperties {

    /** Cross-encoder model (ONNX, Xenova export of bge-reranker-base). */
    private String modelUri =
        "https://hf-mirror.com/Xenova/bge-reranker-base/resolve/main/onnx/model_quantized.onnx";

    /** Tokenizer for the cross-encoder model. */
    private String tokenizerUri =
        "https://hf-mirror.com/Xenova/bge-reranker-base/resolve/main/tokenizer.json";

    /** Local cache directory for the downloaded model files. */
    private String cacheDirectory = System.getProperty("user.home") + "/.cache/offer-pilot/reranker";

    /** Keep the top N documents after re-ranking (defaults to the retriever top-K). */
    private int topK = 5;

    /** Maximum token length for (query + document) pairs. */
    private int maxLength = 512;

    public String getModelUri() {
        return modelUri;
    }

    public void setModelUri(String modelUri) {
        this.modelUri = modelUri;
    }

    public String getTokenizerUri() {
        return tokenizerUri;
    }

    public void setTokenizerUri(String tokenizerUri) {
        this.tokenizerUri = tokenizerUri;
    }

    public String getCacheDirectory() {
        return cacheDirectory;
    }

    public void setCacheDirectory(String cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }
}
