package com.eyki.offerpilot.interview;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.service.RateLimitService;
import com.eyki.offerpilot.interview.domain.InterviewSession;
import com.eyki.offerpilot.interview.dto.StartInterviewRequest;
import com.eyki.offerpilot.interview.enums.SessionStatus;
import com.eyki.offerpilot.interview.repository.InterviewQuestionRepository;
import com.eyki.offerpilot.interview.repository.InterviewSessionRepository;
import com.eyki.offerpilot.interview.service.InterviewSessionManager;
import com.eyki.offerpilot.interview.service.impl.InterviewServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InterviewServiceImplTest {

    @Mock
    private InterviewSessionRepository sessionRepository;
    @Mock
    private InterviewQuestionRepository questionRepository;
    @Mock
    private InterviewSessionManager sessionManager;
    @Mock
    private AuthService authService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private RagService ragService;

    @InjectMocks
    private InterviewServiceImpl interviewService;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(1L);
        when(authService.getCurrentUserEntity()).thenReturn(currentUser);
    }

    @Test
    void createSession_shouldThrow_whenActiveSessionExists() {
        when(rateLimitService.canStartInterview(anyLong())).thenReturn(true);

        InterviewSession activeSession = new InterviewSession();
        activeSession.setId(1L);
        activeSession.setStatus(SessionStatus.IN_PROGRESS.getCode());
        when(sessionRepository.findActiveByUserId(1L)).thenReturn(List.of(activeSession));

        StartInterviewRequest request = new StartInterviewRequest();
        request.setResumeId(1L);
        request.setPositionId(1L);

        assertThrows(BusinessException.class, () -> interviewService.createSession(request));
    }

    @Test
    void getSession_shouldThrow_whenNotOwner() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(2L);
        when(sessionRepository.selectById(1L)).thenReturn(session);

        assertThrows(BusinessException.class, () -> interviewService.getSession(1L));
    }

    @Test
    void endSession_shouldThrow_whenAlreadyClosed() {
        InterviewSession session = new InterviewSession();
        session.setId(1L);
        session.setUserId(1L);
        session.setStatus(SessionStatus.COMPLETED.getCode());
        when(sessionRepository.selectById(1L)).thenReturn(session);

        assertThrows(BusinessException.class, () -> interviewService.endSession(1L));
    }
}