package com.eyki.offerpilot.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple rate limiter using in-memory ConcurrentHashMap.
 * Resets daily via @Scheduled.
 * TODO: Phase 8 — migrate to Redis when needed.
 */
@Slf4j
@Service
public class RateLimitService {

    /** Daily report generation limit for free tier */
    private static final int DAILY_REPORT_LIMIT = 3;

    /** Daily interview session limit for free tier */
    private static final int DAILY_INTERVIEW_LIMIT = 1;

    private final Map<String, AtomicInteger> reportCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> interviewCounts = new ConcurrentHashMap<>();

    /**
     * Check if the user can generate a report today.
     */
    public boolean canGenerateReport(Long userId) {
        return getCount(reportCounts, userId) < DAILY_REPORT_LIMIT;
    }

    /**
     * Record a report generation.
     */
    public void recordReportGeneration(Long userId) {
        incrementCount(reportCounts, userId);
        log.debug("用户报告生成计数: userId={}, count={}", userId, getCount(reportCounts, userId));
    }

    /**
     * Check if the user can start an interview today.
     */
    public boolean canStartInterview(Long userId) {
        return getCount(interviewCounts, userId) < DAILY_INTERVIEW_LIMIT;
    }

    /**
     * Record an interview session start.
     */
    public void recordInterviewStart(Long userId) {
        incrementCount(interviewCounts, userId);
        log.debug("用户面试计数: userId={}, count={}", userId, getCount(interviewCounts, userId));
    }

    /**
     * Get the remaining report count for the user.
     */
    public int getRemainingReports(Long userId) {
        return Math.max(0, DAILY_REPORT_LIMIT - getCount(reportCounts, userId));
    }

    /**
     * Get the remaining interview count for the user.
     */
    public int getRemainingInterviews(Long userId) {
        return Math.max(0, DAILY_INTERVIEW_LIMIT - getCount(interviewCounts, userId));
    }

    /**
     * Reset all counts daily at midnight.
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void resetDailyCounts() {
        reportCounts.clear();
        interviewCounts.clear();
        log.info("每日限流计数已重置");
    }

    private int getCount(Map<String, AtomicInteger> map, Long userId) {
        return map.computeIfAbsent(userId.toString(), k -> new AtomicInteger(0)).get();
    }

    private void incrementCount(Map<String, AtomicInteger> map, Long userId) {
        map.computeIfAbsent(userId.toString(), k -> new AtomicInteger(0)).incrementAndGet();
    }
}