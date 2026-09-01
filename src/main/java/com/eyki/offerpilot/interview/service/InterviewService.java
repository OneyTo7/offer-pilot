package com.eyki.offerpilot.interview.service;

import com.eyki.offerpilot.interview.dto.AnswerRequest;
import com.eyki.offerpilot.interview.dto.InterviewQuestionVO;
import com.eyki.offerpilot.interview.dto.InterviewSummaryVO;
import com.eyki.offerpilot.interview.dto.SessionVO;
import com.eyki.offerpilot.interview.dto.StartInterviewRequest;
import com.eyki.offerpilot.interview.dto.StartRoundResponse;
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
     * Get the current pending question for a session.
     */
    InterviewQuestionVO getCurrentQuestion(Long sessionId);

    /**
     * Start a round and generate the first question.
     */
    StartRoundResponse startRound(Long sessionId);

    /**
     * Submit an answer and get AI feedback via SSE streaming + next question.
     */
    SseEmitter answer(AnswerRequest request);

    /**
     * Skip the current question and get the next one.
     */
    StartRoundResponse skip(Long sessionId);

    /**
     * End the session early.
     */
    void endSession(Long sessionId);

    /**
     * Get interview summary.
     */
    InterviewSummaryVO getSummary(Long sessionId);
}