package com.eyki.offerpilot.aicore.rag;

import org.springframework.context.annotation.Configuration;

/**
 * Query augmenter configuration for RAG. Note: ContextualQueryAugmenter is in the spring-ai-rag module. Add
 * spring-ai-rag dependency to pom.xml to enable.
 */
@Configuration
public class QueryAugmenterConfig {

    // ContextualQueryAugmenter requires spring-ai-rag on classpath.
    // Uncomment when the dependency is added:
    //
    // @Bean
    // public ContextualQueryAugmenter contextualQueryAugmenter() {
    //     return ContextualQueryAugmenter.builder()
    //             .allowEmptyContext(false)
    //             .build();
    // }
}