package com.eyki.offerpilot.aicore.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.eyki.offerpilot.aicore.rag.config.ReRankerProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;

/**
 * Re-ranker degradation tests: with model URIs pointing at nonexistent files the re-ranker
 * fails to load and must return documents in their original order (graceful degradation).
 */
class BgeCrossEncoderReRankerTest {

    private BgeCrossEncoderReRanker createDegradedReRanker() {
        ReRankerProperties properties = new ReRankerProperties();
        properties.setModelUri("file:/nonexistent/reranker/model.onnx");
        properties.setTokenizerUri("file:/nonexistent/reranker/tokenizer.json");
        properties.setCacheDirectory(System.getProperty("java.io.tmpdir") + "/offer-pilot-test-reranker");
        return new BgeCrossEncoderReRanker(properties);
    }

    @Test
    void process_shouldKeepOriginalOrder_whenModelUnavailable() {
        BgeCrossEncoderReRanker reRanker = createDegradedReRanker();
        List<Document> docs = List.of(new Document("第一段"), new Document("第二段"), new Document("第三段"));

        List<Document> result = reRanker.process(new Query("测试查询"), docs);

        assertEquals(3, result.size());
        assertSame(docs.get(0), result.get(0), "模型不可用时必须保持原始顺序");
        assertSame(docs.get(1), result.get(1));
        assertSame(docs.get(2), result.get(2));
    }

    @Test
    void process_shouldReturnAsIs_forNullOrSingleDocument() {
        BgeCrossEncoderReRanker reRanker = createDegradedReRanker();

        // null 输入不抛异常
        assertEquals(null, reRanker.process(new Query("q"), null));
        // 单文档无需重排
        List<Document> single = List.of(new Document("只有一段"));
        assertSame(single, reRanker.process(new Query("q"), single));
    }
}
