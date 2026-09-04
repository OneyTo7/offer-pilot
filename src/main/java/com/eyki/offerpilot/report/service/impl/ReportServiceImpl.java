package com.eyki.offerpilot.report.service.impl;

import com.eyki.offerpilot.aicore.prompt.ReportPrompt;
import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.service.RateLimitService;
import com.eyki.offerpilot.position.domain.TargetPosition;
import com.eyki.offerpilot.position.repository.PositionRepository;
import com.eyki.offerpilot.report.domain.Report;
import com.eyki.offerpilot.report.dto.ReportAnalysisData;
import com.eyki.offerpilot.report.dto.ReportContent;
import com.eyki.offerpilot.report.dto.ReportRequest;
import com.eyki.offerpilot.report.dto.ReportVO;
import com.eyki.offerpilot.report.repository.ReportRepository;
import com.eyki.offerpilot.report.service.ReportService;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report service implementation. Generates AI-powered resume-vs-position match reports
 * with structured output (BeanOutputConverter), asynchronously after creation.
 * Provides CRUD operations with user-level data isolation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ResumeRepository resumeRepository;
    private final PositionRepository positionRepository;
    private final AuthService authService;
    private final AiService aiService;
    private final RagService ragService;
    private final RateLimitService rateLimitService;
    private final UserTokenUsageService tokenUsageService;
    private final ObjectMapper objectMapper;
    private final Executor reportTaskExecutor;

    @Override
    @Transactional
    public ReportVO create(ReportRequest request) {
        User user = authService.getCurrentUserEntity();
        Long userId = user.getId();
        boolean hasOwnApiKey = user.getApiKey() != null && !user.getApiKey().isBlank();

        // Check rate limit only when user does NOT have their own API key
        if (!hasOwnApiKey) {
            if (!rateLimitService.canGenerateReport(userId)) {
                throw BusinessException.of(429, "今日报告生成次数已用完（免费版每日限 " + RateLimitService.DAILY_REPORT_LIMIT + " 次）"
                    + "，可配置自己的 API Key 解锁无限使用");
            }
            tokenUsageService.checkRemainingOrThrow(userId);
        }

        // Verify resume and position belong to user
        Resume resume = resumeRepository.selectById(request.getResumeId());
        BusinessException.checkOwnership(resume != null && resume.getUserId().equals(userId), BusinessException::resumeNotFound);
        TargetPosition position = positionRepository.selectById(request.getPositionId());
        BusinessException.checkOwnership(position != null && position.getUserId().equals(userId), BusinessException::positionNotFound);

        Report report = new Report();
        report.setUserId(userId);
        report.setResumeId(request.getResumeId());
        report.setPositionId(request.getPositionId());
        report.setStatus(0); // GENERATING
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.insert(report);

        // Record rate limit only for platform-key users
        if (!hasOwnApiKey) {
            rateLimitService.recordReportGeneration(userId);
        }

        // Generate report asynchronously
        // Capture API config before async block (Sa-Token ThreadLocal not available in CompletableFuture)
        String capturedApiKey = user.getApiKey();
        String capturedApiBaseUrl = user.getApiBaseUrl();
        String capturedApiModel = user.getApiModel();
        Report finalReport = report;
        CompletableFuture.runAsync(() -> {
            try {
                generateAiReport(finalReport, resume, position, capturedApiKey, capturedApiBaseUrl, capturedApiModel);
            } catch (Exception e) {
                log.error("报告 AI 生成失败: reportId={}", finalReport.getId(), e);
                finalReport.setStatus(2); // FAILED
                finalReport.setErrorMessage("AI 生成失败: " + e.getMessage());
                reportRepository.updateById(finalReport);
            }
        }, reportTaskExecutor);

        log.info("报告创建成功: reportId={}, userId={}, hasOwnApiKey={}", report.getId(), userId, hasOwnApiKey);
        return toReportVO(report, resume.getName(), position.getTitle());
    }

    @Override
    public ReportVO getDetail(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Report report = reportRepository.selectById(id);
        BusinessException.checkOwnership(report != null && report.getUserId().equals(userId), BusinessException::reportNotFound);
        String resumeName = resolveResumeName(report.getResumeId());
        String positionTitle = resolvePositionTitle(report.getPositionId());
        return toReportVO(report, resumeName, positionTitle);
    }

    @Override
    public List<ReportVO> listMyReports() {
        Long userId = authService.getCurrentUserEntity().getId();
        List<Report> reports = reportRepository.findByUserId(userId);
        if (reports.isEmpty()) {
            return List.of();
        }

        // Batch-load resume names and position titles to avoid N+1
        Set<Long> resumeIds = reports.stream().map(Report::getResumeId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> positionIds = reports.stream().map(Report::getPositionId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> resumeNameMap = Collections.emptyMap();
        if (!resumeIds.isEmpty()) {
            resumeNameMap = resumeRepository.selectBatchIds(resumeIds).stream()
                .filter(r -> r.getName() != null)
                .collect(Collectors.toMap(Resume::getId, Resume::getName));
        }

        Map<Long, String> positionTitleMap = Collections.emptyMap();
        if (!positionIds.isEmpty()) {
            positionTitleMap = positionRepository.selectBatchIds(positionIds).stream()
                .filter(p -> p.getTitle() != null)
                .collect(Collectors.toMap(TargetPosition::getId, TargetPosition::getTitle));
        }

        final Map<Long, String> finalResumeNameMap = resumeNameMap;
        final Map<Long, String> finalPositionTitleMap = positionTitleMap;

        return reports.stream()
            .map(r -> toReportVO(r,
                finalResumeNameMap.getOrDefault(r.getResumeId(), "未知"),
                finalPositionTitleMap.getOrDefault(r.getPositionId(), "未知")))
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Report report = reportRepository.selectById(id);
        BusinessException.checkOwnership(report != null && report.getUserId().equals(userId), BusinessException::reportNotFound);
        reportRepository.deleteById(id);
        log.info("报告删除成功: reportId={}", id);
    }

    /**
     * Generate the report content via AI using resume and position context.
     * Runs asynchronously after the initial report creation response.
     *
     * @param userApiKey     the user's API key (may be null/blank, falls back to platform key)
     * @param userApiBaseUrl the user's API base URL (null → default)
     * @param userApiModel   the user's model name (null → default)
     */
    private void generateAiReport(Report report, Resume resume, TargetPosition position, String userApiKey,
        String userApiBaseUrl, String userApiModel) {
        // Build prompt from resume and position data
        String name = resume.getName() != null ? resume.getName() : "未知";
        String techStack = resume.getSkills() != null ? resume.getSkills() : (resume.getSummary() != null ? resume.getSummary() : "未提供");
        String workYears = "未知";
        String education = "未提供";
        String summary = resume.getParsedText() != null
            ? resume.getParsedText().substring(0, Math.min(resume.getParsedText().length(), 500)) : "未提供";

        String positionTitle = position != null ? position.getTitle() : "未知";
        String company = position != null ? position.getCompany() : "未知";
        String positionDesc = position != null ? position.getJdText() : "未知";

        String userPrompt = String.format(ReportPrompt.USER_PROMPT_TEMPLATE,
            name, techStack, workYears, education, summary,
            positionTitle, company, positionDesc);

        // RAG 策略（统一路径）：RetrievalAugmentationAdvisor 自动检索知识库并注入行业标准参考，
        // 通过 context 传 user_id 过滤表达式实现用户级隔离；三种 key 配置行为一致
        // （用户自备 key 时由 ApiKeyRoutingAdvisor 拦截模型调用，RAG/记忆增强在路由前已完成）
        Map<String, Object> context = new HashMap<>();
        context.put("chat_memory_conversation_id", "report-" + report.getId());
        context.put("vector_store_filter_expression", ragService.buildUserFilter(report.getUserId()));
        context.put("user_id", report.getUserId());
        if (userApiBaseUrl != null) {
            context.put("api_base_url", userApiBaseUrl);
        }
        if (userApiModel != null) {
            context.put("api_model", userApiModel);
        }

        // Call AI with structured output via BeanOutputConverter (auto JSON Schema + markdown stripping)
        ReportAnalysisData data = aiService.chatWithEntity(
            ReportPrompt.SYSTEM_PROMPT, userPrompt, userApiKey, ReportAnalysisData.class, context);
        parseAndSaveReport(report, data);
    }

    /**
     * Save the structured ReportAnalysisData to the report entity.
     * Saves the full parsed result as analysis_data for frontend rendering,
     * while also extracting individual fields for backward compatibility.
     */
    private void parseAndSaveReport(Report report, ReportAnalysisData data) {
        try {
            // Serialize clean JSON for frontend to render the rich report
            String cleanJson = objectMapper.writeValueAsString(data);
            report.setAnalysisData(cleanJson);

            // Extract individual fields for backward compatibility
            report.setMatchScore(BigDecimal.valueOf(data.matchScore()));
            report.setHighlights(objectMapper.writeValueAsString(data.competitiveAdvantages()));
            report.setWeaknesses(objectMapper.writeValueAsString(data.weaknesses()));
            report.setFullReport(data.fullReport());

            report.setStatus(1); // COMPLETED
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.updateById(report);
            log.info("报告 AI 生成完成: reportId={}", report.getId());
        } catch (Exception e) {
            log.error("解析 AI 报告 JSON 失败: reportId={}", report.getId(), e);
            report.setStatus(2); // FAILED
            report.setErrorMessage("报告解析失败: " + e.getMessage());
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.updateById(report);
        }
    }

    private ReportVO toReportVO(Report report, String resumeName, String positionTitle) {
        ReportVO.ReportVOBuilder builder =
            ReportVO.builder().id(report.getId()).resumeId(report.getResumeId()).resumeName(resumeName)
                .positionId(report.getPositionId()).positionTitle(positionTitle)
                .status(report.getStatus()).errorMessage(report.getErrorMessage()).createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt());

        if (report.getStatus() == 1) {
            // Parse JSON fields
            ReportContent.ReportContentBuilder contentBuilder = ReportContent.builder();
            if (report.getMatchScore() != null) {
                contentBuilder.matchScore(report.getMatchScore().doubleValue());
            }
            if (report.getTechStackAnalysis() != null) {
                try {
                    contentBuilder.techStackAnalysis(
                        objectMapper.readValue(report.getTechStackAnalysis(), ReportContent.TechStackAnalysis.class));
                } catch (Exception e) {
                    log.warn("解析 techStackAnalysis JSON 失败: reportId={}", report.getId(), e);
                }
            }
            if (report.getHighlights() != null) {
                try {
                    contentBuilder.highlights(
                        objectMapper.readValue(report.getHighlights(), new TypeReference<List<String>>() {}));
                } catch (Exception e) {
                    log.warn("解析 highlights JSON 失败: reportId={}", report.getId(), e);
                }
            }
            if (report.getWeaknesses() != null) {
                try {
                    contentBuilder.weaknesses(
                        objectMapper.readValue(report.getWeaknesses(), new TypeReference<List<String>>() {}));
                } catch (Exception e) {
                    log.warn("解析 weaknesses JSON 失败: reportId={}", report.getId(), e);
                }
            }
            contentBuilder.fullReport(report.getFullReport());
            // Pass through the full AI analysis data for the new rich report frontend
            contentBuilder.analysisData(report.getAnalysisData());
            builder.content(contentBuilder.build());
        }

        return builder.build();
    }

    private String resolveResumeName(Long resumeId) {
        if (resumeId == null) return "未知";
        Resume resume = resumeRepository.selectById(resumeId);
        return resume != null && resume.getName() != null ? resume.getName() : "未知";
    }

    private String resolvePositionTitle(Long positionId) {
        if (positionId == null) return "未知";
        TargetPosition position = positionRepository.selectById(positionId);
        return position != null && position.getTitle() != null ? position.getTitle() : "未知";
    }
}