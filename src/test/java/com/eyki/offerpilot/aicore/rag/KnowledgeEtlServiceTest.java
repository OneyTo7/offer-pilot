package com.eyki.offerpilot.aicore.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;

class KnowledgeEtlServiceTest {

    private KnowledgeEtlService etlService;

    @BeforeEach
    void setUp() throws Exception {
        @SuppressWarnings("unchecked") ObjectProvider<Object> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        var constructor = KnowledgeEtlService.class.getDeclaredConstructor(ObjectProvider.class);
        constructor.setAccessible(true);
        etlService = (KnowledgeEtlService)constructor.newInstance(emptyProvider);
    }

    @Test
    void extract_shouldParseMarkdown() {
        String markdown = "# 第一章 Java 基础\n\nJava 是面向对象的语言。\n\n## 1.1 集合框架\n\nArrayList 基于数组实现。\n\n## 1.2 并发\n\nsynchronized 关键字。";
        ByteArrayResource resource = new ByteArrayResource(markdown.getBytes(), "java-basics.md");

        List<Document> docs = etlService.extract(resource, "java-basics.md");

        assertNotNull(docs);
        assertFalse(docs.isEmpty());
        // MarkdownDocumentReader 按标题分块：每个标题节为一个 Document
        assertTrue(docs.size() >= 3, "markdown 应按标题分块，实际: " + docs.size());
        String allText = String.join(" ", docs.stream().map(Document::getText).toList());
        assertTrue(allText.contains("Java"));
        assertTrue(allText.contains("ArrayList"));
    }

    @Test
    void extract_shouldParsePlainTxtViaTika() {
        String txt = "MySQL 索引原理\nB+ 树结构适合范围查询。\n覆盖索引可以避免回表。\n";
        ByteArrayResource resource = new ByteArrayResource(txt.getBytes(), "mysql-notes.txt");

        List<Document> docs = etlService.extract(resource, "mysql-notes.txt");

        assertNotNull(docs);
        assertFalse(docs.isEmpty());
        String allText = String.join(" ", docs.stream().map(Document::getText).toList());
        assertTrue(allText.contains("B+ 树"));
        assertTrue(allText.contains("覆盖索引"));
    }

    @Test
    void index_shouldReturnEmpty_whenVectorStoreUnavailable() {
        List<Document> docs = List.of(new Document("测试内容"));

        List<Document> chunks = etlService.index(docs, 1L, "doc-1", java.util.Map.of());

        assertNotNull(chunks);
        assertEquals(0, chunks.size());
    }
}
