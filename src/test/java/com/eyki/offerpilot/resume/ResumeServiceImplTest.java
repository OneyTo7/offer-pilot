package com.eyki.offerpilot.resume;

import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.enums.ResumeStatus;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.eyki.offerpilot.resume.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private ResumeServiceImpl resumeService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        currentUser.setEmail("test@example.com");
        when(authService.getCurrentUserEntity()).thenReturn(currentUser);
    }

    @Test
    void getDetail_shouldThrow_whenNotOwner() {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(2L); // different user
        when(resumeRepository.selectById(1L)).thenReturn(resume);

        assertThrows(BusinessException.class, () -> resumeService.getDetail(1L));
    }

    @Test
    void listMyResumes_shouldReturnUserResumes() {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUserId(1L);
        resume.setName("test.pdf");
        resume.setStatus(ResumeStatus.COMPLETED.getCode());
        when(resumeRepository.findByUserId(1L)).thenReturn(List.of(resume));

        var results = resumeService.listMyResumes();

        assertEquals(1, results.size());
        assertEquals("test.pdf", results.get(0).getName());
    }
}