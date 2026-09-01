package com.eyki.offerpilot.interview.controller;

import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.interview.dto.AnswerRequest;
import com.eyki.offerpilot.interview.dto.InterviewQuestionVO;
import com.eyki.offerpilot.interview.dto.InterviewSummaryVO;
import com.eyki.offerpilot.interview.dto.SessionVO;
import com.eyki.offerpilot.interview.dto.StartInterviewRequest;
import com.eyki.offerpilot.interview.dto.StartRoundResponse;
import com.eyki.offerpilot.interview.service.InterviewService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Interview controller — manages mock interview sessions with 3 rounds of 10 questions each.
 * Supports SSE streaming for real-time AI feedback on answers, session lifecycle
 * (start round, skip, end), and summary retrieval.
 */
@RestController
@RequestMapping("/api/v1/interviews")
@RequiredArgsConstructor
public class InterviewController {

    private final InterviewService interviewService;

    /**
     * Create a new interview session.
     */
    @PostMapping
    public ApiResult<SessionVO> create(@Valid @RequestBody StartInterviewRequest request) {
        SessionVO session = interviewService.createSession(request);
        return ApiResult.success("面试创建成功", session);
    }

    /**
     * List all interview sessions for the current user.
     */
    @GetMapping
    public ApiResult<List<SessionVO>> listMySessions() {
        List<SessionVO> sessions = interviewService.listMySessions();
        return ApiResult.success(sessions);
    }

    /**
     * Get interview session detail.
     */
    @GetMapping("/{id}")
    public ApiResult<SessionVO> getSession(@PathVariable Long id) {
        SessionVO session = interviewService.getSession(id);
        return ApiResult.success(session);
    }

    /**
     * Get the current pending question for a session.
     */
    @GetMapping("/{id}/current-question")
    public ApiResult<InterviewQuestionVO> getCurrentQuestion(@PathVariable Long id) {
        InterviewQuestionVO question = interviewService.getCurrentQuestion(id);
        return ApiResult.success(question);
    }

    /**
     * Start a round and generate the first question.
     */
    @PostMapping("/{id}/start-round")
    public ApiResult<StartRoundResponse> startRound(@PathVariable Long id) {
        StartRoundResponse response = interviewService.startRound(id);
        return ApiResult.success(response);
    }

    /**
     * Submit an answer and get AI feedback via SSE streaming.
     *
     * @param id      the interview session ID
     * @param request the answer payload containing question_id and answer text
     * @return SSE emitter streaming events: feedback_token (streamed text), feedback_done ({"feedback":"...","score":N}),
     *         next_question (JSON), complete
     */
    @PostMapping("/{id}/answer")
    public SseEmitter answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest request) {
        request.setSessionId(id);
        return interviewService.answer(request);
    }

    /**
     * Skip the current question and get the next one.
     */
    @PostMapping("/{id}/skip")
    public ApiResult<StartRoundResponse> skip(@PathVariable Long id) {
        StartRoundResponse response = interviewService.skip(id);
        return ApiResult.success(response);
    }

    /**
     * End the session early.
     */
    @PostMapping("/{id}/end")
    public ApiResult<?> endSession(@PathVariable Long id) {
        interviewService.endSession(id);
        return ApiResult.success("面试已结束");
    }

    /**
     * Get interview summary.
     */
    @GetMapping("/{id}/summary")
    public ApiResult<InterviewSummaryVO> getSummary(@PathVariable Long id) {
        InterviewSummaryVO summary = interviewService.getSummary(id);
        return ApiResult.success(summary);
    }
}