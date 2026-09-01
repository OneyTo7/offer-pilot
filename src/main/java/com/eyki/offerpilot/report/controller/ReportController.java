package com.eyki.offerpilot.report.controller;

import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.report.dto.ReportRequest;
import com.eyki.offerpilot.report.dto.ReportVO;
import com.eyki.offerpilot.report.service.ReportService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assessment report controller — generates and retrieves AI-powered resume-vs-position
 * match reports. Reports include match score, tech stack analysis, highlights, and weaknesses.
 */
@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping
    public ApiResult<ReportVO> create(@Valid @RequestBody ReportRequest request) {
        ReportVO report = reportService.create(request);
        return ApiResult.success("报告生成成功", report);
    }

    @GetMapping
    public ApiResult<List<ReportVO>> listMyReports() {
        List<ReportVO> reports = reportService.listMyReports();
        return ApiResult.success(reports);
    }

    @GetMapping("/{id}")
    public ApiResult<ReportVO> getDetail(@PathVariable Long id) {
        ReportVO report = reportService.getDetail(id);
        return ApiResult.success(report);
    }

    @DeleteMapping("/{id}")
    public ApiResult<?> delete(@PathVariable Long id) {
        reportService.delete(id);
        return ApiResult.success("删除成功");
    }
}