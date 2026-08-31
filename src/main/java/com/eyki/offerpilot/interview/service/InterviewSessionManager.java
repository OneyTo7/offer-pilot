package com.eyki.offerpilot.interview.service;

import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.interview.domain.InterviewSession;
import com.eyki.offerpilot.interview.enums.SessionStatus;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Interview state machine manager. Handles status transitions, expiration checks, and round/question tracking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewSessionManager {

    private static final int MAX_ROUNDS = 3;
    private static final int MAX_QUESTIONS_PER_ROUND = 10;
    private static final int EXPIRATION_HOURS = 1;

    /**
     * Check if the session can be operated on. Throws appropriate exception if the session is expired or closed.
     */
    public void checkSessionActive(InterviewSession session) {
        if (session == null) {
            throw BusinessException.interviewNotFound();
        }

        // Check expiration
        if (session.getExpiredAt() != null && LocalDateTime.now().isAfter(session.getExpiredAt())) {
            // Mark as expired
            session.setStatus(SessionStatus.EXPIRED.getCode());
            session.setFinishedAt(LocalDateTime.now());
            log.info("面试会话已过期: sessionId={}", session.getId());
            throw BusinessException.interviewExpired();
        }

        // Check if session is already closed
        int status = session.getStatus();
        if (status != SessionStatus.IN_PROGRESS.getCode()) {
            throw BusinessException.interviewClosed();
        }
    }

    /**
     * Initialize a new session.
     */
    public void initSession(InterviewSession session) {
        session.setCurrentRound(1);
        session.setCurrentQuestion(0);
        session.setTotalQuestions(0);
        session.setDurationSeconds(0);
        session.setStatus(SessionStatus.IN_PROGRESS.getCode());
        session.setStartedAt(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Start a new round. Returns the starting question index.
     */
    public int startRound(InterviewSession session) {
        session.setCurrentQuestion(1);
        session.setUpdatedAt(LocalDateTime.now());
        return 1;
    }

    /**
     * Advance to the next question after an answer or skip. Returns true if there's a next question, false if the
     * round/session is complete.
     */
    public boolean advanceQuestion(InterviewSession session) {
        int currentQuestion = session.getCurrentQuestion();
        int currentRound = session.getCurrentRound();
        int totalQuestions = session.getTotalQuestions() + 1;
        session.setTotalQuestions(totalQuestions);

        if (currentQuestion < MAX_QUESTIONS_PER_ROUND) {
            // Same round, next question
            session.setCurrentQuestion(currentQuestion + 1);
            session.setUpdatedAt(LocalDateTime.now());
            return true;
        }

        // Round finished
        if (currentRound < MAX_ROUNDS) {
            // Next round
            session.setCurrentRound(currentRound + 1);
            session.setCurrentQuestion(1);
            session.setUpdatedAt(LocalDateTime.now());
            return true;
        }

        // All rounds complete — finish session
        completeSession(session);
        return false;
    }

    /**
     * End the session early.
     */
    public void endSessionEarly(InterviewSession session) {
        session.setStatus(SessionStatus.COMPLETED.getCode());
        session.setFinishedAt(LocalDateTime.now());
        if (session.getStartedAt() != null) {
            session.setDurationSeconds(
                (int)java.time.Duration.between(session.getStartedAt(), session.getFinishedAt()).getSeconds());
        }
        session.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Complete the session normally.
     */
    public void completeSession(InterviewSession session) {
        session.setStatus(SessionStatus.COMPLETED.getCode());
        session.setFinishedAt(LocalDateTime.now());
        if (session.getStartedAt() != null) {
            session.setDurationSeconds(
                (int)java.time.Duration.between(session.getStartedAt(), session.getFinishedAt()).getSeconds());
        }
        session.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Set the expiration time for the session (e.g., on interruption).
     */
    public void setExpiration(InterviewSession session) {
        session.setExpiredAt(LocalDateTime.now().plusHours(EXPIRATION_HOURS));
        session.setUpdatedAt(LocalDateTime.now());
    }

    public int getMaxRounds() {
        return MAX_ROUNDS;
    }

    public int getMaxQuestionsPerRound() {
        return MAX_QUESTIONS_PER_ROUND;
    }
}