package com.eyki.offerpilot.report.service;

import com.eyki.offerpilot.report.dto.ReportRequest;
import com.eyki.offerpilot.report.dto.ReportVO;

import java.util.List;

public interface ReportService {

    ReportVO create(ReportRequest request);

    ReportVO getDetail(Long id);

    List<ReportVO> listMyReports();

    void delete(Long id);
}