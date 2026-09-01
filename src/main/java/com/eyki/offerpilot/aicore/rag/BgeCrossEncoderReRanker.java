package com.eyki.offerpilot.aicore.rag;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import com.eyki.offerpilot.aicore.rag.config.ReRankerProperties;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.ai.transformers.ResourceCacheService;

/**
 * Cross-encoder re-ranker implementing Spring AI's {@link DocumentPostProcessor} extension point
 * (there is no built-in ReRanker in Spring AI 2.x — it is a documented post-retrieval extension point).
 *
 * <p>Loads a BGE-reranker ONNX model and tokenizer (cached locally, model URIs configurable via
 * {@link ReRankerProperties}). For each retrieved document it scores the (query, document) pair
 * with the cross-encoder, then re-sorts documents by relevance score — re-ranking the
 * bi-encoder similarity results from the vector store.</p>
 *
 * <p>Model loading is best-effort: if the model cannot be downloaded or inference fails,
 * the processor logs a warning and returns the documents in their original order so the
 * RAG pipeline degrades gracefully.</p>
 */
public class BgeCrossEncoderReRanker implements DocumentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(BgeCrossEncoderReRanker.class);

    private final ReRankerProperties properties;
    private final ResourceCacheService cacheService;

    private OrtEnvironment environment;
    private OrtSession session;
    private HuggingFaceTokenizer tokenizer;
    private volatile boolean ready;

    public BgeCrossEncoderReRanker(ReRankerProperties properties) {
        this.properties = properties;
        this.cacheService = new ResourceCacheService(new File(properties.getCacheDirectory()));
        initModel();
    }

    /**
     * Load the tokenizer and ONNX model from the configured URIs (cached locally).
     * Any failure only disables the re-ranker — the RAG pipeline keeps working.
     */
    private void initModel() {
        try {
            var tokenizerOptions = Map.of("maxLength", String.valueOf(properties.getMaxLength()));
            this.tokenizer = HuggingFaceTokenizer.newInstance(
                cacheService.getCachedResource(properties.getTokenizerUri()).getInputStream(), tokenizerOptions);

            this.environment = OrtEnvironment.getEnvironment();
            var sessionOptions = new OrtSession.SessionOptions();
            this.session = environment.createSession(
                cacheService.getCachedResource(properties.getModelUri()).getContentAsByteArray(), sessionOptions);

            log.info("BGE Cross-Encoder ReRanker 加载成功: model={}, cacheDir={}", properties.getModelUri(),
                properties.getCacheDirectory());
            this.ready = true;
        } catch (Exception e) {
            log.warn("BGE Cross-Encoder ReRanker 加载失败，重排降级为原始顺序: {}", e.getMessage());
            this.ready = false;
        }
    }

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (!ready || documents == null || documents.size() <= 1) {
            return documents;
        }

        try {
            return rerank(query.text(), documents);
        } catch (Exception e) {
            log.warn("重排推理失败，降级为原始顺序: {}", e.getMessage());
            return documents;
        }
    }

    private List<Document> rerank(String queryText, List<Document> documents) {
        // Encode each (query, document) pair — the cross-encoder distinguishes the two segments
        List<Encoding> encodings = new ArrayList<>(documents.size());
        for (Document doc : documents) {
            encodings.add(tokenizer.encode(queryText, doc.getText()));
        }

        int batchSize = encodings.size();
        long[][] inputIds = new long[batchSize][];
        long[][] attentionMask = new long[batchSize][];
        long[][] tokenTypeIds = new long[batchSize][];
        for (int i = 0; i < batchSize; i++) {
            inputIds[i] = encodings.get(i).getIds();
            attentionMask[i] = encodings.get(i).getAttentionMask();
            tokenTypeIds[i] = encodings.get(i).getTypeIds();
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        try (OnnxTensor inputIdsTensor = OnnxTensor.createTensor(environment, inputIds);
             OnnxTensor attentionMaskTensor = OnnxTensor.createTensor(environment, attentionMask);
             OnnxTensor tokenTypeIdsTensor = OnnxTensor.createTensor(environment, tokenTypeIds)) {
            inputs.put("input_ids", inputIdsTensor);
            inputs.put("attention_mask", attentionMaskTensor);
            inputs.put("token_type_ids", tokenTypeIdsTensor);
            // Some converted models omit token_type_ids — drop unknown inputs
            inputs.keySet().removeIf(name -> !session.getInputNames().contains(name));

            double[] scores = new double[batchSize];
            try (OrtSession.Result results = session.run(inputs)) {
                OnnxValue output = results.get(0);
                float[][] logits = (float[][]) output.getValue();
                for (int i = 0; i < batchSize; i++) {
                    scores[i] = sigmoid(logits[i][0]);
                }
            }

            // Re-sort by relevance score (descending), keeping at most topK documents
            int topK = Math.min(properties.getTopK(), documents.size());
            List<ScoredDocument> scored = new ArrayList<>(batchSize);
            for (int i = 0; i < batchSize; i++) {
                scored.add(new ScoredDocument(documents.get(i), scores[i]));
            }
            scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());

            List<Document> reranked = new ArrayList<>(topK);
            for (int i = 0; i < topK; i++) {
                reranked.add(scored.get(i).document());
            }
            log.debug("重排完成: {} 个文档 → 保留 top {}，最高分={}", documents.size(), topK,
                scored.get(0).score());
            return reranked;
        } catch (Exception e) {
            log.warn("重排推理异常: {}", e.getMessage());
            return documents;
        }
    }

    private static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private record ScoredDocument(Document document, double score) {}
}
