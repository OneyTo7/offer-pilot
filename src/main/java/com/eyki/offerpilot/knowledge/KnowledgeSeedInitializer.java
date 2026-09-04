package com.eyki.offerpilot.knowledge;

import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.knowledge.domain.ContentType;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocument;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocumentRepository;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 系统知识库种子数据初始化器。
 * <p>
 * 应用启动时自动检测系统知识库是否为空，若为空则从 resources/knowledge-seed/ 目录加载 Markdown 文件并创建系统级知识文档。
 * 这些文档会自动索引到 pgvector，供 AI 小助手 RAG 检索。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSeedInitializer {

    private static final String SEED_DIR = "knowledge-seed/";
    /** 系统种子文档的管理员用户 ID（id=1 的 admin 用户） */
    private static final long ADMIN_USER_ID = 1L;

    private final KnowledgeDocumentRepository repository;
    private final RagService ragService;

    @PostConstruct
    public void init() {
        try {
            // 检查系统知识库是否已有文档，避免重复创建
            List<KnowledgeDocument> existing = repository.findByScope("system");
            if (!existing.isEmpty()) {
                log.info("系统知识库已存在 {} 个文档，跳过种子数据初始化", existing.size());
                return;
            }

            // 加载 seed 目录下的 Markdown 文件
            loadSeedFiles();
        } catch (Exception e) {
            log.warn("系统知识库种子数据初始化失败（不影响启动）: {}", e.getMessage());
        }
    }

    private void loadSeedFiles() {
        // 尝试加载 platform-intro.md
        String filename = "platform-intro.md";
        String content = readFile(SEED_DIR + filename);
        if (content == null) {
            log.warn("种子文件不存在: {}", filename);
            return;
        }

        // 从文件名提取标题
        String title = "面壁 OfferPilot 平台介绍";

        // 创建领域实体（scope=system）
        KnowledgeDocument doc = KnowledgeDocument.create(ADMIN_USER_ID, title, content, ContentType.FILE, "system");
        repository.save(doc);

        // 索引到 pgvector
        try {
            Map<String, Object> metadata = Map.of(
                "document_id", doc.getId().toString(),
                "title", doc.getTitle(),
                "user_id", String.valueOf(ADMIN_USER_ID),
                "source", "knowledge_base",
                "scope", "system"
            );
            ragService.indexDocument(doc.getContent(), ADMIN_USER_ID, null, metadata);
            doc.markIndexed();
            repository.save(doc);
            log.info("系统知识库种子文档创建并索引成功: docId={}, title={}", doc.getId(), title);
        } catch (Exception e) {
            log.error("系统知识库种子文档索引失败: title={}", title, e);
            doc.markFailed(e.getMessage());
            repository.save(doc);
        }
    }

    private String readFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return null;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("读取种子文件失败: path={}", path, e);
            return null;
        }
    }
}