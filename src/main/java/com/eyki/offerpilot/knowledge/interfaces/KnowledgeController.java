package com.eyki.offerpilot.knowledge.interfaces;

import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.knowledge.application.KnowledgeApplicationService;
import com.eyki.offerpilot.knowledge.application.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库管理 — REST 接口层。
 *
 * 职责：HTTP 请求解析、响应包装、参数校验。
 * 不包含业务逻辑，直接委派给 Application Service。
 */
@RestController
@RequestMapping("/api/v1/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeApplicationService knowledgeService;

    /**
     * 创建知识文档（上传文本内容，自动分片索引到向量库）。
     */
    @PostMapping
    public ApiResult<KnowledgeDocumentVO> create(@Valid @RequestBody KnowledgeUploadRequest request) {
        KnowledgeDocumentVO doc = knowledgeService.create(request);
        return ApiResult.success("知识文档创建成功", doc);
    }

    /**
     * 获取用户的知识文档列表。
     */
    @GetMapping
    public ApiResult<List<KnowledgeDocumentVO>> listMyDocuments() {
        List<KnowledgeDocumentVO> documents = knowledgeService.listMyDocuments();
        return ApiResult.success(documents);
    }

    /**
     * 获取知识文档详情（含全文内容）。
     */
    @GetMapping("/{id}")
    public ApiResult<KnowledgeDocumentDetailVO> getDetail(@PathVariable Long id) {
        KnowledgeDocumentDetailVO detail = knowledgeService.getDetail(id);
        return ApiResult.success(detail);
    }

    /**
     * 删除知识文档（同时删除向量库中的分片）。
     */
    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResult.success("删除成功");
    }

    /**
     * 语义搜索知识库。
     */
    @PostMapping("/search")
    public ApiResult<List<KnowledgeSearchResult>> search(@Valid @RequestBody KnowledgeSearchRequest request) {
        List<KnowledgeSearchResult> results = knowledgeService.search(request);
        return ApiResult.success(results);
    }
}