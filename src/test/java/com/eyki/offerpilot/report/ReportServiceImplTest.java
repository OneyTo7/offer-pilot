package com.eyki.offerpilot.report;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.position.repository.PositionRepository;
import com.eyki.offerpilot.report.domain.Report;
import com.eyki.offerpilot.report.dto.ReportRequest;
import com.eyki.offerpilot.report.repository.ReportRepository;
import com.eyki.offerpilot.report.service.impl.ReportServiceImpl;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ResumeRepository resumeRepository;
    @Mock
    private PositionRepository positionRepository;
    @Mock
    private AuthService authService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authService.getCurrentUserEntity()).thenReturn(currentUser);
    }

    @Test
    void create_shouldThrow_whenResumeNotOwned() {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(2L); // different user
        when(resumeRepository.selectById(1L)).thenReturn(resume);

        ReportRequest request = new ReportRequest();
        request.setResumeId(1L);
        request.setPositionId(1L);

        assertThrows(BusinessException.class, () -> reportService.create(request));
    }

    @Test
    void getDetail_shouldThrow_whenNotOwner() {
        Report report = new Report();
        report.setId(1L);
        report.setUserId(2L);
        when(reportRepository.selectById(1L)).thenReturn(report);

        assertThrows(BusinessException.class, () -> reportService.getDetail(1L));
    }
}