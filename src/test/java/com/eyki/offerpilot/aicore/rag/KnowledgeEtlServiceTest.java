package com.eyki.offerpilot.aicore.rag;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

class KnowledgeEtlServiceTest {

    private KnowledgeEtlService service() {
        @SuppressWarnings("unchecked") ObjectProvider<VectorStore> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        return new KnowledgeEtlService(emptyProvider);
    }

    @Test
    void extract_shouldPrependHeadingTitle_whenMarkdown() {
        String md = "## 1. 说一下进程和线程的区别\n**答案：**\n进程是操作系统资源分配的最小单位，线程是CPU调度执行的最小单位。";

        List<Document> docs = service().extract(byteResource("test.md", md), "test.md");

        assertTrue(docs.stream().anyMatch(d -> d.getText().contains("1. 说一下进程和线程的区别")),
            "分片内容应包含题目");
        assertTrue(docs.stream().anyMatch(d -> d.getText().contains("进程是操作系统资源分配的最小单位")),
            "分片内容应包含答案");
    }

    @Test
    void extract_shouldKeepPlainTxtContent() {
        String txt = "纯文本内容，没有标题结构。";

        List<Document> docs = service().extract(byteResource("note.txt", txt), "note.txt");

        assertTrue(docs.stream().anyMatch(d -> d.getText().contains("纯文本内容")), "TXT 内容应被保留");
    }

    private Resource byteResource(String filename, String content) {
        return new InputStreamResource(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
