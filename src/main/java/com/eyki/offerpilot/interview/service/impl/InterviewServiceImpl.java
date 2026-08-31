package com.eyki.offerpilot.interview.service.impl;

import com.eyki.offerpilot.aicore.memory.MysqlChatMemory;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.interview.domain.InterviewQuestion;
import com.eyki.offerpilot.interview.domain.InterviewSession;
import com.eyki.offerpilot.interview.dto.*;
import com.eyki.offerpilot.interview.enums.InterviewRound;
import com.eyki.offerpilot.interview.enums.QuestionStatus;
import com.eyki.offerpilot.interview.repository.InterviewQuestionRepository;
import com.eyki.offerpilot.interview.repository.InterviewSessionRepository;
import com.eyki.offerpilot.interview.service.InterviewService;
import com.eyki.offerpilot.interview.service.InterviewSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewSessionManager sessionManager;
    private final AuthService authService;
    private final MysqlChatMemory chatMemoryStore;

    @Override
    @Transactional
    public SessionVO createSession(StartInterviewRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();

        // Check for existing active session
        List<InterviewSession> activeSessions = sessionRepository.findActiveByUserId(userId);
        if (!activeSessions.isEmpty()) {
            throw BusinessException.badRequest("您已有进行中的面试，请先完成或结束当前面试");
        }

        InterviewSession session = new InterviewSession();
        session.setUserId(userId);
        session.setResumeId(request.getResumeId());
        session.setPositionId(request.getPositionId());
        sessionManager.initSession(session);
        sessionRepository.insert(session);

        // 注册对话记忆的用户关联
        chatMemoryStore.registerConversation(session.getId().toString(), userId);

        log.info("面试会话创建成功: sessionId={}, userId={}", session.getId(), userId);
        return toSessionVO(session);
    }

    @Override
    public List<SessionVO> listMySessions() {
        Long userId = authService.getCurrentUserEntity().getId();
        return sessionRepository.findByUserId(userId).stream()
                .map(this::toSessionVO)
                .collect(Collectors.toList());
    }

    @Override
    public SessionVO getSession(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(id);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.interviewNotFound();
        }
        return toSessionVO(session);
    }

    @Override
    @Transactional
    public SseEmitter startRound(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.interviewNotFound();
        }
        sessionManager.checkSessionActive(session);

        int round = session.getCurrentRound();
        sessionManager.startRound(session);
        sessionRepository.updateById(session);

        // Generate first question
        InterviewQuestion question = generateQuestion(session, round, 1);
        questionRepository.insert(question);

        // Create SSE emitter and send the first question
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        CompletableFuture.runAsync(() -> {
            try {
                InterviewEvent event = InterviewEvent.nextQuestion(
                        question.getId(), round, 1, question.getQuestionText());
                emitter.send(SseEmitter.event()
                        .name(event.getType())
                        .data(event));
                emitter.complete();
            } catch (IOException e) {
                log.error("SSE 发送失败: sessionId={}", sessionId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    @Transactional
    public SseEmitter answer(AnswerRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(request.getSessionId());
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.interviewNotFound();
        }
        sessionManager.checkSessionActive(session);

        // Find and validate the question
        InterviewQuestion question = questionRepository.selectById(request.getQuestionId());
        if (question == null || !question.getSessionId().equals(session.getId())) {
            throw BusinessException.badRequest("题目不存在");
        }
        if (question.getStatus() != QuestionStatus.PENDING.getCode()) {
            throw BusinessException.badRequest("该题目已作答或已跳过");
        }

        // Save answer
        question.setUserAnswer(request.getAnswer());
        question.setStatus(QuestionStatus.ANSWERED.getCode());

        // Generate feedback (stub for now)
        question.setFeedback(generateStubFeedback(question, session));
        question.setScore(generateStubScore());
        question.setUpdatedAt(LocalDateTime.now());
        questionRepository.updateById(question);

        // Advance to next question
        boolean hasNext = sessionManager.advanceQuestion(session);
        sessionRepository.updateById(session);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        CompletableFuture.runAsync(() -> {
            try {
                // Send feedback
                emitter.send(SseEmitter.event()
                        .name("feedback")
                        .data(InterviewEvent.feedback(question.getFeedback())));

                if (hasNext) {
                    // Generate next question
                    int round = session.getCurrentRound();
                    int questionIndex = session.getCurrentQuestion();
                    InterviewQuestion nextQuestion = generateQuestion(session, round, questionIndex);
                    nextQuestion.setSessionId(session.getId());
                    questionRepository.insert(nextQuestion);

                    InterviewEvent event = InterviewEvent.nextQuestion(
                            nextQuestion.getId(), round, questionIndex, nextQuestion.getQuestionText());
                    emitter.send(SseEmitter.event()
                            .name(event.getType())
                            .data(event));
                } else {
                    // Session complete
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(InterviewEvent.complete("面试已完成！")));
                }

                emitter.complete();
            } catch (IOException e) {
                log.error("SSE 发送失败: sessionId={}", request.getSessionId(), e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    @Transactional
    public SseEmitter skip(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.interviewNotFound();
        }
        sessionManager.checkSessionActive(session);

        // Find current question
        int round = session.getCurrentRound();
        int questionIndex = session.getCurrentQuestion();
        List<InterviewQuestion> questions = questionRepository.findBySessionIdAndRound(sessionId, round);
        InterviewQuestion currentQuestion = questions.stream()
                .filter(q -> q.getQuestionIndex().equals(questionIndex)
                        && q.getStatus() == QuestionStatus.PENDING.getCode())
                .findFirst().orElse(null);

        if (currentQuestion != null) {
            currentQuestion.setStatus(QuestionStatus.SKIPPED.getCode());
            currentQuestion.setUpdatedAt(LocalDateTime.now());
            questionRepository.updateById(currentQuestion);
        }

        // Advance to next question
        boolean hasNext = sessionManager.advanceQuestion(session);
        sessionRepository.updateById(session);

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);
        CompletableFuture.runAsync(() -> {
            try {
                if (hasNext) {
                    // Generate next question
                    int nextRound = session.getCurrentRound();
                    int nextIndex = session.getCurrentQuestion();
                    InterviewQuestion nextQuestion = generateQuestion(session, nextRound, nextIndex);
                    nextQuestion.setSessionId(session.getId());
                    questionRepository.insert(nextQuestion);

                    InterviewEvent event = InterviewEvent.nextQuestion(
                            nextQuestion.getId(), nextRound, nextIndex, nextQuestion.getQuestionText());
                    emitter.send(SseEmitter.event()
                            .name(event.getType())
                            .data(event));
                } else {
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(InterviewEvent.complete("面试已完成！")));
                }
                emitter.complete();
            } catch (IOException e) {
                log.error("SSE 发送失败: sessionId={}", sessionId, e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @Override
    @Transactional
    public void endSession(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.interviewNotFound();
        }

        if (session.getStatus() != 0) {
            throw BusinessException.interviewClosed();
        }

        sessionManager.endSessionEarly(session);
        sessionRepository.updateById(session);
        log.info("面试会话已结束: sessionId={}", sessionId);
    }

    @Override
    public InterviewSummaryVO getSummary(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw BusinessException.interviewNotFound();
        }

        List<InterviewQuestion> allQuestions = questionRepository.findBySessionId(sessionId);

        int totalQuestions = allQuestions.size();
        long answered = allQuestions.stream().filter(q -> q.getStatus() == QuestionStatus.ANSWERED.getCode()).count();
        long skipped = allQuestions.stream().filter(q -> q.getStatus() == QuestionStatus.SKIPPED.getCode()).count();

        List<InterviewSummaryVO.RoundSummary> roundSummaries = buildRoundSummaries(allQuestions);

        return InterviewSummaryVO.builder()
                .sessionId(sessionId)
                .totalRounds(sessionManager.getMaxRounds())
                .totalQuestions(totalQuestions)
                .answeredQuestions((int) answered)
                .skippedQuestions((int) skipped)
                .durationSeconds(session.getDurationSeconds() != null ? session.getDurationSeconds() : 0)
                .summary(session.getSummary())
                .rounds(roundSummaries)
                .build();
    }

    // ========== Helper methods ==========

    private InterviewQuestion generateQuestion(InterviewSession session, int round, int questionIndex) {
        // TODO: Phase 6 — call AiService to generate real questions
        InterviewRound interviewRound = InterviewRound.fromCode(round);
        String questionText = generateStubQuestion(round, questionIndex, interviewRound.getDescription());

        InterviewQuestion question = new InterviewQuestion();
        question.setSessionId(session.getId());
        question.setRound(round);
        question.setQuestionIndex(questionIndex);
        question.setQuestionText(questionText);
        question.setStatus(QuestionStatus.PENDING.getCode());
        question.setCreatedAt(LocalDateTime.now());
        question.setUpdatedAt(LocalDateTime.now());
        return question;
    }

    private String generateStubQuestion(int round, int questionIndex, String roundDescription) {
        // Stub questions — replaced by real AI in production
        String[][] questions = {
                // Round 1: Basic technical ability
                {
                        "请介绍一下你最熟悉的 Java 技术栈，以及为什么选择它们？",
                        "请解释一下 Spring Boot 自动配置的原理。",
                        "什么是 IOC 和 DI？请用实际例子说明。",
                        "请解释一下 HashMap 的实现原理和扩容机制。",
                        "Java 中的线程池是如何工作的？核心参数有哪些？",
                        "请说明 MySQL 的索引原理，以及如何优化慢查询。",
                        "什么是事务？请解释 ACID 特性。",
                        "请解释一下什么是 RESTful API，以及设计原则。",
                        "JVM 内存模型是怎样的？如何排查内存泄漏？",
                        "请谈谈你对微服务架构的理解，以及它和单体架构的区别。"
                },
                // Round 2: Project experience & depth
                {
                        "请分享一个你主导的最有挑战性的项目，以及你在其中扮演的角色。",
                        "在项目中，你们是如何进行技术选型的？请举例说明。",
                        "请解释一下你在项目中使用的缓存策略，以及如何解决缓存穿透/击穿/雪崩。",
                        "你们项目中的分布式事务是如何处理的？",
                        "请描述一个你遇到过的性能瓶颈，以及你是如何解决的。",
                        "消息队列在你们的项目中是如何使用的？请比较一下常见的 MQ 产品。",
                        "请详细说明你们项目的数据库表设计和分库分表策略。",
                        "你是如何保证代码质量的？请介绍你们的 CI/CD 流程。",
                        "请解释一下你负责的某个核心模块的设计思路。",
                        "在团队协作中，你是如何处理技术债务的？"
                },
                // Round 3: System design & comprehensive
                {
                        "请设计一个高并发秒杀系统，需要考虑哪些关键点？",
                        "如果让你设计一个类似微信的即时通讯系统，你会如何设计架构？",
                        "请设计一个分布式配置中心，需要考虑高可用和一致性。",
                        "如何设计一个支持海量数据的日志收集和分析系统？",
                        "请设计一个短链接生成系统，需要考虑哪些因素？",
                        "如果让你重构一个遗留系统，你会如何规划？",
                        "请设计一个 API 网关，需要考虑哪些功能和非功能需求？",
                        "如何设计一个可靠的分布式定时任务调度系统？",
                        "请谈谈你对 DDD（领域驱动设计）的理解，以及在实际项目中的应用。",
                        "如果系统出现大量 502 错误，请描述你的排查思路。"
                }
        };

        int roundIdx = round - 1;
        int qIdx = questionIndex - 1;
        if (roundIdx >= 0 && roundIdx < questions.length && qIdx >= 0 && qIdx < questions[roundIdx].length) {
            return questions[roundIdx][qIdx];
        }
        return String.format("【%s】请介绍一下你在相关技术领域的经验（第 %d 题）", roundDescription, questionIndex);
    }

    private String generateStubFeedback(InterviewQuestion question, InterviewSession session) {
        // TODO: Phase 6 — call AiService to generate real feedback
        return String.format("感谢你的回答。你对这个问题有基本的理解，建议可以进一步深入实践。"
                + "（AI 面试反馈功能待接入，当前为模拟反馈）");
    }

    private BigDecimal generateStubScore() {
        // Generate a random-ish score between 5.0 and 9.0
        return BigDecimal.valueOf(5.0 + Math.random() * 4.0)
                .setScale(1, java.math.RoundingMode.HALF_UP);
    }

    private List<InterviewSummaryVO.RoundSummary> buildRoundSummaries(List<InterviewQuestion> questions) {
        return questions.stream()
                .collect(Collectors.groupingBy(InterviewQuestion::getRound))
                .entrySet().stream()
                .map(entry -> {
                    int round = entry.getKey();
                    List<InterviewQuestion> roundQuestions = entry.getValue();
                    InterviewRound interviewRound = InterviewRound.fromCode(round);

                    List<InterviewSummaryVO.QuestionSummary> questionSummaries = roundQuestions.stream()
                            .map(q -> InterviewSummaryVO.QuestionSummary.builder()
                                    .id(q.getId())
                                    .questionIndex(q.getQuestionIndex())
                                    .questionText(q.getQuestionText())
                                    .userAnswer(q.getUserAnswer())
                                    .feedback(q.getFeedback())
                                    .score(q.getScore() != null ? q.getScore().doubleValue() : null)
                                    .status(QuestionStatus.fromCode(q.getStatus()).name())
                                    .build())
                            .collect(Collectors.toList());

                    return InterviewSummaryVO.RoundSummary.builder()
                            .round(round)
                            .roundName(interviewRound.getName())
                            .totalQuestions(roundQuestions.size())
                            .answeredQuestions((int) roundQuestions.stream()
                                    .filter(q -> q.getStatus() == QuestionStatus.ANSWERED.getCode()).count())
                            .skippedQuestions((int) roundQuestions.stream()
                                    .filter(q -> q.getStatus() == QuestionStatus.SKIPPED.getCode()).count())
                            .questions(questionSummaries)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private SessionVO toSessionVO(InterviewSession session) {
        return SessionVO.builder()
                .id(session.getId())
                .resumeId(session.getResumeId())
                .positionId(session.getPositionId())
                .currentRound(session.getCurrentRound())
                .currentQuestion(session.getCurrentQuestion())
                .totalQuestions(session.getTotalQuestions())
                .status(session.getStatus())
                .durationSeconds(session.getDurationSeconds())
                .startedAt(session.getStartedAt())
                .finishedAt(session.getFinishedAt())
                .expiredAt(session.getExpiredAt())
                .createdAt(session.getCreatedAt())
                .build();
    }
}