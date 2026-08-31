package com.eyki.offerpilot.report.service.impl;

import cn.hutool.json.JSONUtil;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.report.domain.Report;
import com.eyki.offerpilot.report.dto.ReportContent;
import com.eyki.offerpilot.report.dto.ReportRequest;
import com.eyki.offerpilot.report.dto.ReportVO;
import com.eyki.offerpilot.report.repository.ReportRepository;
import com.eyki.offerpilot.report.service.ReportService;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.eyki.offerpilot.position.domain.TargetPosition;
import com.eyki.offerpilot.position.repository.PositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ReportRepository reportRepository;
    private final ResumeRepository resumeRepository;
    private final PositionRepository positionRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public ReportVO create(ReportRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();

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

        // TODO: Phase 5 — async AI report generation via AiService
        // For now, generate stub report immediately
        generateStubReport(report);

        log.info("报告创建成功: reportId={}, userId={}", report.getId(), userId);
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
        return reportRepository.findByUserId(userId).stream()
                .map(this::toReportVO)
                .collect(Collectors.toList());
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

    private void generateStubReport(Report report) {
        // Stub report data — replaced by real AI in Phase 5
        report.setMatchScore(new BigDecimal("72.50"));
        report.setTechStackAnalysis(JSONUtil.toJsonStr(
                ReportContent.TechStackAnalysis.builder()
                        .matched("Java, Spring Boot, MySQL, MyBatis-Plus, Redis")
                        .missing("Docker, Kubernetes, 微服务架构, 消息队列")
                        .recommendation("建议补充容器化和微服务相关经验")
                        .build()
        ));
        report.setHighlights(JSONUtil.toJsonStr(List.of(
                "5年Java开发经验，主导过多个大型项目",
                "熟悉Spring Boot生态，有微服务架构设计经验",
                "具备良好的系统设计能力和团队协作能力"
        )));
        report.setWeaknesses(JSONUtil.toJsonStr(List.of(
                "缺乏云原生技术栈（Docker/K8s）的实战经验",
                "对分布式事务和消息队列的理解需要加强"
        )));
        report.setFullReport("报告生成功能待接入 — AI 服务配置后生效。\n\n"
                + "匹配度: 72.5%\n"
                + "技术栈匹配: Java, Spring Boot, MySQL, MyBatis-Plus, Redis\n"
                + "技术栈缺失: Docker, Kubernetes, 微服务架构, 消息队列\n"
                + "建议: 补充容器化和微服务相关经验");
        report.setStatus(1); // COMPLETED
        reportRepository.updateById(report);
    }

    private ReportVO toReportVO(Report report) {
        ReportVO.ReportVOBuilder builder = ReportVO.builder()
                .id(report.getId())
                .resumeId(report.getResumeId())
                .positionId(report.getPositionId())
                .status(report.getStatus())
                .errorMessage(report.getErrorMessage())
                .createdAt(report.getCreatedAt())
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