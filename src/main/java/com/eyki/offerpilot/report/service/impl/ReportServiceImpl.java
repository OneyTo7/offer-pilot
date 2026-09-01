package com.eyki.offerpilot.report.service.impl;

import cn.hutool.json.JSONUtil;
import com.eyki.offerpilot.aicore.prompt.ReportPrompt;
import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.service.RateLimitService;
import com.eyki.offerpilot.position.domain.TargetPosition;
import com.eyki.offerpilot.position.repository.PositionRepository;
import com.eyki.offerpilot.report.domain.Report;
import com.eyki.offerpilot.report.dto.ReportContent;
import com.eyki.offerpilot.report.dto.ReportRequest;
import com.eyki.offerpilot.report.dto.ReportVO;
import com.eyki.offerpilot.report.repository.ReportRepository;
import com.eyki.offerpilot.report.service.ReportService;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Report service implementation. Generates AI-powered resume-vs-position match reports
 * (currently returns stub data until AI integration is complete). Provides CRUD operations
 * with user-level data isolation.
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
    private final ObjectMapper objectMapper;

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
                    + "，可配置自己的 DeepSeek API Key 解锁无限使用");
            }
        }

        // Verify resume and position belong to user
        Resume resume = resumeRepository.selectById(request.getResumeId());
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
        }
        TargetPosition position = positionRepository.selectById(request.getPositionId());
        if (position == null || !position.getUserId().equals(userId)) {
            throw BusinessException.positionNotFound();
        }

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
        // Capture API key before async block (Sa-Token ThreadLocal not available in CompletableFuture)
        String capturedApiKey = user.getApiKey();
        Report finalReport = report;
        CompletableFuture.runAsync(() -> {
            try {
                generateAiReport(finalReport, resume, position, capturedApiKey);
            } catch (Exception e) {
                log.error("报告 AI 生成失败: reportId={}", finalReport.getId(), e);
                finalReport.setStatus(2); // FAILED
                finalReport.setErrorMessage("AI 生成失败: " + e.getMessage());
                reportRepository.updateById(finalReport);
            }
        });

        log.info("报告创建成功: reportId={}, userId={}, hasOwnApiKey={}", report.getId(), userId, hasOwnApiKey);
        return toReportVO(report);
    }

    @Override
    public ReportVO getDetail(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Report report = reportRepository.selectById(id);
        if (report == null || !report.getUserId().equals(userId)) {
            throw BusinessException.reportNotFound();
        }
        return toReportVO(report);
    }

    @Override
    public List<ReportVO> listMyReports() {
        Long userId = authService.getCurrentUserEntity().getId();
        return reportRepository.findByUserId(userId).stream().map(this::toReportVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Report report = reportRepository.selectById(id);
        if (report == null || !report.getUserId().equals(userId)) {
            throw BusinessException.reportNotFound();
        }
        reportRepository.deleteById(id);
        log.info("报告删除成功: reportId={}", id);
    }

    /**
     * Generate the report content via AI using resume and position context.
     * Runs asynchronously after the initial report creation response.
     *
     * @param userApiKey the user's DeepSeek API key (may be null/blank, falls back to platform key)
     */
    private void generateAiReport(Report report, Resume resume, TargetPosition position, String userApiKey) {
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

        // RAG: 检索知识库，获取行业标准或技能要求作为匹配度评估参考
        try {
            String searchQuery = positionTitle + " " + techStack + " 技能要求 行业标准";
            List<Document> relevantDocs = ragService.search(searchQuery, report.getUserId(), 3);
            if (!relevantDocs.isEmpty()) {
                String ragContext = relevantDocs.stream()
                    .map(doc -> "- " + doc.getText())
                    .collect(Collectors.joining("\n\n"));
                userPrompt += "\n\n知识库参考信息（可作为行业标准参考，用于评估简历匹配度）：\n" + ragContext;
            }
        } catch (Exception e) {
            log.warn("RAG 检索报告参考失败: {}", e.getMessage());
        }

        String response = aiService.chat(ReportPrompt.SYSTEM_PROMPT, userPrompt, userApiKey);
        parseAndSaveReport(report, response);
    }

    /**
     * Parse AI JSON response and save structured data to the report entity.
     */
    private void parseAndSaveReport(Report report, String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // Parse match_score
            JsonNode scoreNode = root.get("match_score");
            if (scoreNode != null) {
                report.setMatchScore(BigDecimal.valueOf(scoreNode.asDouble()));
            }

            // Parse tech_stack_analysis
            JsonNode techStackNode = root.get("tech_stack_analysis");
            if (techStackNode != null) {
                report.setTechStackAnalysis(objectMapper.writeValueAsString(techStackNode));
            }

            // Parse highlights
            JsonNode highlightsNode = root.get("highlights");
            if (highlightsNode != null && highlightsNode.isArray()) {
                report.setHighlights(objectMapper.writeValueAsString(highlightsNode));
            }

            // Parse weaknesses
            JsonNode weaknessesNode = root.get("weaknesses");
            if (weaknessesNode != null && weaknessesNode.isArray()) {
                report.setWeaknesses(objectMapper.writeValueAsString(weaknessesNode));
            }

            // Parse full_report
            JsonNode fullReportNode = root.get("full_report");
            if (fullReportNode != null) {
                report.setFullReport(fullReportNode.asText());
            }

            report.setStatus(1); // COMPLETED
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.updateById(report);
            log.info("报告 AI 生成完成: reportId={}", report.getId());
        } catch (Exception e) {
            log.error("解析 AI 报告 JSON 失败: reportId={}, response={}", report.getId(), response, e);
            report.setStatus(2); // FAILED
            report.setErrorMessage("报告解析失败: " + e.getMessage());
            report.setUpdatedAt(LocalDateTime.now());
            reportRepository.updateById(report);
        }
    }

    private ReportVO toReportVO(Report report) {
        ReportVO.ReportVOBuilder builder =
            ReportVO.builder().id(report.getId()).resumeId(report.getResumeId()).positionId(report.getPositionId())
                .status(report.getStatus()).errorMessage(report.getErrorMessage()).createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt());

        if (report.getStatus() == 1) {
            // Parse JSON fields
            ReportContent.ReportContentBuilder contentBuilder = ReportContent.builder();
            if (report.getMatchScore() != null) {
                contentBuilder.matchScore(report.getMatchScore().doubleValue());
            }
            if (report.getTechStackAnalysis() != null) {
                contentBuilder.techStackAnalysis(
                    JSONUtil.toBean(report.getTechStackAnalysis(), ReportContent.TechStackAnalysis.class));
            }
            if (report.getHighlights() != null) {
                contentBuilder.highlights(JSONUtil.parseArray(report.getHighlights()).toList(String.class));
            }
            if (report.getWeaknesses() != null) {
                contentBuilder.weaknesses(JSONUtil.parseArray(report.getWeaknesses()).toList(String.class));
            }
            contentBuilder.fullReport(report.getFullReport());
            builder.content(contentBuilder.build());
        }

        return builder.build();
    }
}