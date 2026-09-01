package com.eyki.offerpilot;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    // Re-ranker model files point at nonexistent local paths: initModel() fails fast
    // (no network in CI/test environments) and the re-ranker degrades to original order.
    "spring.ai.rag.reranker.model-uri=file:/nonexistent/reranker/model.onnx",
    "spring.ai.rag.reranker.tokenizer-uri=file:/nonexistent/reranker/tokenizer.json"
})
class OfferPilotApplicationTests {

    /**
     * Mock the local ONNX embedding model so the context loads without downloading
     * the BGE model files from HuggingFace (no network in CI/test environments).
     */
    @MockitoBean
    private EmbeddingModel embeddingModel;

    @Test
    void contextLoads() {
    }

}
