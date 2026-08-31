package com.eyki.offerpilot.interview.controller;

import com.eyki.offerpilot.common.model.ApiResult;
import com.eyki.offerpilot.interview.dto.*;
import com.eyki.offerpilot.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

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
     * Start a round and generate the first question.
     * Returns SSE stream with the first question event.
     */
    @PostMapping("/{id}/start-round")
    public SseEmitter startRound(@PathVariable Long id) {
        return interviewService.startRound(id);
    }

    /**
     * Submit an answer and get AI feedback + next question via SSE.
     */
    @PostMapping("/{id}/answer")
    public SseEmitter answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest request) {
        // Ensure sessionId in path matches request body
        request.setSessionId(id);
        return interviewService.answer(request);
    }

    /**
     * Skip the current question and get the next one via SSE.
     */
    @PostMapping("/{id}/skip")
    public SseEmitter skip(@PathVariable Long id) {
        return interviewService.skip(id);
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