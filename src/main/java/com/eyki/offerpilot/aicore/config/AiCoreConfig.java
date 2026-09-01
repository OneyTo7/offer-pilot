package com.eyki.offerpilot.aicore.config;

import com.eyki.offerpilot.aicore.advisor.MyLogAdvisor;
import com.eyki.offerpilot.aicore.advisor.ReReadingAdvisor;
import com.eyki.offerpilot.aicore.advisor.SafeValidAdvisor;
import com.eyki.offerpilot.aicore.memory.PgChatMemory;
import com.eyki.offerpilot.aicore.rag.BgeCrossEncoderReRanker;
import com.eyki.offerpilot.aicore.rag.config.ReRankerProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AI Core configuration. Sets up the ChatClient with the Advisor Chain:
 * <pre>
 * SafeValidAdvisor (order=0) → 输入安全校验
 * ReReadingAdvisor (order=1) → 提示词增强
 * MessageChatMemoryAdvisor (order=2) → 对话记忆自动注入
 * RetrievalAugmentationAdvisor (order=3) → 自动 RAG 检索+增强（当 pgvector 可用时）
 * MyLogAdvisor (order=4) → 请求耗时日志
 * </pre>
 */
@Configuration
@EnableConfigurationProperties(ReRankerProperties.class)
public class AiCoreConfig {

    /**
     * Cross-encoder re-ranker. Model loading is best-effort (see BgeCrossEncoderReRanker):
     * if the ONNX model cannot be downloaded the re-ranker degrades to the original order
     * and the RAG pipeline keeps working.
     */
    @Bean
    public BgeCrossEncoderReRanker bgeCrossEncoderReRanker(ReRankerProperties properties) {
        return new BgeCrossEncoderReRanker(properties);
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                  Optional<RetrievalAugmentationAdvisor> ragAdvisor,
                                  MessageChatMemoryAdvisor memoryAdvisor) {
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(new SafeValidAdvisor());
        advisors.add(new ReReadingAdvisor());
        advisors.add(memoryAdvisor);
        ragAdvisor.ifPresent(advisors::add);
        advisors.add(new MyLogAdvisor());
        return builder.defaultAdvisors(advisors.toArray(new Advisor[0])).build();
    }

    /**
     * ChatClient without the RAG advisor, for pure extraction scenarios (e.g. resume parsing).
     * <p>RetrievalAugmentationAdvisor has no per-call disable switch — it runs on every
     * ChatClient invocation. Resume parsing must not be augmented with knowledge-base
     * content (it would pollute the structured extraction), so it uses this dedicated
     * client instead. Spring AI's recommended pattern for different advisor sets per use case.</p>
     */
    @Bean
    public ChatClient chatClientNoRag(ChatClient.Builder builder) {
        return builder.defaultAdvisors(new SafeValidAdvisor(), new ReReadingAdvisor(), new MyLogAdvisor()).build();
    }

    /**
     * MessageChatMemoryAdvisor uses PgChatMemory to persist and inject conversation history.
     * The conversationId is passed via advisor context param {@code "conversation_id"}.
     */
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(PgChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).order(2).build();
    }

    /**
     * RetrievalAugmentationAdvisor enables automatic RAG for all ChatClient calls.
     * Uses pgvector as the document store, filtering by user_id via context param
     * {@code "vector_store_filter_expression"}.
     *
     * <p>Only created when a VectorStore bean is available (pgvector configured).
     * Without it, ChatClient calls proceed without automatic RAG.</p>
     */
    @Bean
    public RetrievalAugmentationAdvisor retrievalAugmentationAdvisor(Optional<VectorStore> vectorStore,
        Optional<BgeCrossEncoderReRanker> reRanker) {
        return vectorStore.map(vs -> {
            VectorStoreDocumentRetriever retriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vs)
                .similarityThreshold(0.5)
                .topK(5)
                .build();

            ContextualQueryAugmenter augmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)
                .build();

            RetrievalAugmentationAdvisor.Builder advisorBuilder = RetrievalAugmentationAdvisor.builder()
                .documentRetriever(retriever)
                .queryAugmenter(augmenter)
                .order(3);

            // Post-retrieval pipeline: cross-encoder re-ranking of the retrieved documents
            reRanker.ifPresent(r -> advisorBuilder.documentPostProcessors(List.of(r)));

            return advisorBuilder.build();
        }).orElse(null);
    }
}