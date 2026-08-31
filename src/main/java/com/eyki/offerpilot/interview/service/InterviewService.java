package com.eyki.offerpilot.interview.service;

import com.eyki.offerpilot.interview.dto.AnswerRequest;
import com.eyki.offerpilot.interview.dto.InterviewSummaryVO;
import com.eyki.offerpilot.interview.dto.SessionVO;
import com.eyki.offerpilot.interview.dto.StartInterviewRequest;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface InterviewService {

    /**
     * Create a new interview session.
     */
    SessionVO createSession(StartInterviewRequest request);

    /**
     * List all sessions for the current user.
     */
    List<SessionVO> listMySessions();

    /**
     * Get session detail.
     */
    SessionVO getSession(Long id);

    /**
     * Start a round and generate the first question. Returns an SSE emitter that streams the first question event.
     */
    SseEmitter startRound(Long sessionId);

    /**
     * Submit an answer and get AI feedback + next question via SSE.
     */
    SseEmitter answer(AnswerRequest request);

    /**
     * Skip the current question and get the next one via SSE.
     */
    SseEmitter skip(Long sessionId);

    /**
     * End the session early.
     */
    void endSession(Long sessionId);

    /**
     * Get interview summary.
     */
    InterviewSummaryVO getSummary(Long sessionId);
}