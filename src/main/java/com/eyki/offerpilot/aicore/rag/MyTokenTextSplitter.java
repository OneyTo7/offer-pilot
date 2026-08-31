package com.eyki.offerpilot.aicore.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

import java.util.List;

/**
 * Wrapper around Spring AI's TokenTextSplitter with pre-configured chunk size and overlap.
 * chunk_size=500, overlap=50
 */
public class MyTokenTextSplitter {

    private final TokenTextSplitter splitter;

    public MyTokenTextSplitter() {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(50)
                .build();
    }

    public MyTokenTextSplitter(int chunkSize, int overlap) {
        this.splitter = TokenTextSplitter.builder()
                .withChunkSize(chunkSize)
                .withMinChunkSizeChars(overlap)
                .build();
    }

    public List<Document> split(Document document) {
        return splitter.apply(List.of(document));
    }

    public List<Document> split(List<Document> documents) {
        return splitter.apply(documents);
    }
}