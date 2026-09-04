package com.eyki.offerpilot.interview.service.impl;

import com.eyki.offerpilot.aicore.memory.PgChatMemory;
import com.eyki.offerpilot.aicore.prompt.InterviewPrompt;
import com.eyki.offerpilot.aicore.prompt.QuestionFeedbackPrompt;
import com.eyki.offerpilot.aicore.rag.RagService;
import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.aicore.usage.service.UserTokenUsageService;
import com.eyki.offerpilot.auth.domain.User;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import com.eyki.offerpilot.common.service.RateLimitService;
import com.eyki.offerpilot.interview.domain.InterviewQuestion;
import com.eyki.offerpilot.interview.domain.InterviewSession;
import com.eyki.offerpilot.interview.dto.AnswerRequest;
import com.eyki.offerpilot.interview.dto.InterviewQuestionVO;
import com.eyki.offerpilot.interview.dto.InterviewSummaryVO;
import com.eyki.offerpilot.interview.dto.SessionVO;
import com.eyki.offerpilot.interview.dto.StartInterviewRequest;
import com.eyki.offerpilot.interview.dto.StartRoundResponse;
import com.eyki.offerpilot.interview.enums.InterviewRound;
import com.eyki.offerpilot.interview.enums.QuestionStatus;
import com.eyki.offerpilot.interview.repository.InterviewQuestionRepository;
import com.eyki.offerpilot.interview.repository.InterviewSessionRepository;
import com.eyki.offerpilot.interview.service.InterviewService;
import com.eyki.offerpilot.interview.service.InterviewSessionManager;
import com.eyki.offerpilot.position.domain.TargetPosition;
import com.eyki.offerpilot.position.repository.PositionRepository;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;


/**
 * Interview service implementation. Manages the full interview lifecycle: session creation,
 * round progression, question generation (AI-powered with resume/position context),
 * answer submission with SSE-streamed AI feedback, skip, early end, and summary retrieval.
 *
 * <p>The SSE answer flow uses TransactionTemplate for synchronous DB operations followed by
 * async AI streaming via SseEmitter. Sa-Token auth is enforced inside service methods
 * rather than the interceptor, since the SSE async dispatch loses the ThreadLocal context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewSessionRepository sessionRepository;
    private final InterviewQuestionRepository questionRepository;
    private final InterviewSessionManager sessionManager;
    private final AuthService authService;
    private final AiService aiService;
    private final RagService ragService;
    private final RateLimitService rateLimitService;
    private final UserTokenUsageService tokenUsageService;
    private final PgChatMemory chatMemoryStore;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final ResumeRepository resumeRepository;
    private final PositionRepository positionRepository;
    private final Executor sseTaskExecutor;

    private static final long SSE_TIMEOUT = 5 * 60 * 1000L; // 5 minutes
    private static final Pattern SCORE_PATTERN = Pattern.compile("SCORE:\\s*(\\d+(\\.\\d+)?)", Pattern.CASE_INSENSITIVE);

    @Override
    @Transactional
    public SessionVO createSession(StartInterviewRequest request) {
        User user = authService.getCurrentUserEntity();
        Long userId = user.getId();
        boolean hasOwnApiKey = user.getApiKey() != null && !user.getApiKey().isBlank();

        // Check rate limit only when user does NOT have their own API key
        if (!hasOwnApiKey) {
            if (!rateLimitService.canStartInterview(userId)) {
                throw BusinessException.of(429, "今日面试次数已用完（免费版每日限 " + RateLimitService.DAILY_INTERVIEW_LIMIT + " 次）"
                    + "，可配置自己的 API Key 解锁无限使用");
            }
            if (!tokenUsageService.checkRemaining(userId)) {
                throw BusinessException.of(429, "本月 Token 额度已用完（免费版每月限 "
                    + "100K tokens），可配置自己的 API Key 解锁无限使用");
            }
        }

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

        // Record rate limit only for platform-key users
        if (!hasOwnApiKey) {
            rateLimitService.recordInterviewStart(userId);
        }

        // 注册对话记忆的用户关联
        chatMemoryStore.registerConversation(session.getId().toString(), userId);

        log.info("面试会话创建成功: sessionId={}, userId={}, hasOwnApiKey={}", session.getId(), userId, hasOwnApiKey);
        return toSessionVO(session);
    }

    @Override
    public List<SessionVO> listMySessions() {
        Long userId = authService.getCurrentUserEntity().getId();
        return sessionRepository.findByUserId(userId).stream().map(this::toSessionVO).collect(Collectors.toList());
    }

    @Override
    public SessionVO getSession(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(id);
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);
        return toSessionVO(session);
    }

    @Override
    public InterviewQuestionVO getCurrentQuestion(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);

        int round = session.getCurrentRound();
        int questionIndex = session.getCurrentQuestion();

        // Find the pending question for the current round and index
        List<InterviewQuestion> questions = questionRepository.findBySessionIdAndRound(sessionId, round);
        Optional<InterviewQuestion> currentQuestion = questions.stream()
            .filter(q -> q.getQuestionIndex().equals(questionIndex) && q.getStatus() == QuestionStatus.PENDING.getCode())
            .findFirst();

        return currentQuestion.map(this::toQuestionVO).orElse(null);
    }

    @Override
    @Transactional
    public StartRoundResponse startRound(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);
        sessionManager.checkSessionActive(session);

        int round = session.getCurrentRound();
        sessionManager.startRound(session);
        sessionRepository.updateById(session);

        // Generate first question
        InterviewQuestion question = generateQuestion(session, round, 1);
        questionRepository.insert(question);

        return StartRoundResponse.builder()
            .question(toQuestionVO(question))
            .build();
    }

    @Override
    public SseEmitter answer(AnswerRequest request) {
        User user = authService.getCurrentUserEntity();
        Long userId = user.getId();

        // Capture user's API config before the async block (Sa-Token ThreadLocal is not available in async threads)
        String userApiKey = user.getApiKey();
        String userApiBaseUrl = user.getApiBaseUrl();
        String userApiModel = user.getApiModel();

        // Phase 1: Save answer and advance to next question (synchronous, in transaction)
        AnswerContext ctx = transactionTemplate.execute(status -> saveAnswerAndAdvance(request, userId));

        // Phase 2: Create SSE emitter and stream AI feedback (async, no transaction)
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        assert ctx != null;
        InterviewRound interviewRound = InterviewRound.fromCode(ctx.session.getCurrentRound());
        String userPrompt = String.format(QuestionFeedbackPrompt.USER_PROMPT_TEMPLATE,
            ctx.session.getCurrentRound(), interviewRound.getName(),
            ctx.question.getQuestionText(), ctx.question.getUserAnswer());

        // RAG + 记忆策略（统一路径）：RetrievalAugmentationAdvisor 自动检索知识库（user_id 过滤隔离），
        // MessageChatMemoryAdvisor 按 conversation_id 注入前序问答，使反馈评分连贯；
        // 三种 key 配置行为一致（用户自备 key 时由 ApiKeyRoutingAdvisor 拦截模型调用）
        Map<String, Object> context = new HashMap<>();
        context.put("vector_store_filter_expression", ragService.buildUserFilter(userId));
        context.put("chat_memory_conversation_id", ctx.session.getId().toString());
        context.put("user_id", userId);
        if (userApiBaseUrl != null) {
            context.put("api_base_url", userApiBaseUrl);
        }
        if (userApiModel != null) {
            context.put("api_model", userApiModel);
        }

        CompletableFuture.runAsync(() -> {
            try {
                StringBuilder fullFeedback = new StringBuilder();

                aiService.chatStream(QuestionFeedbackPrompt.SYSTEM_PROMPT, userPrompt, userApiKey, context)
                    .doOnNext(token -> {
                        // 跳过 advisor 补发的空内容 usage 收尾 chunk
                        if (token == null || token.isEmpty()) {
                            return;
                        }
                        try {
                            fullFeedback.append(token);
                            emitter.send(SseEmitter.event().name("feedback_token").data(token));
                        } catch (IOException e) {
                            log.error("SSE 发送 feedback_token 失败", e);
                            // 客户端断开时停止流
                            emitter.completeWithError(e);
                            throw new RuntimeException(e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            String completeText = fullFeedback.toString();
                            double score = parseScore(completeText);

                            // Save feedback and score to DB (async, single update no transaction needed)
                            ctx.question.setFeedback(completeText);
                            ctx.question.setScore(BigDecimal.valueOf(score));
                            questionRepository.updateById(ctx.question);

                            // Send feedback_done event
                            String doneJson = String.format("{\"feedback\":%s,\"score\":%s}",
                                escapeJson(completeText), score);
                            emitter.send(SseEmitter.event().name("feedback_done").data(doneJson));

                            if (ctx.nextQuestion != null) {
                                // Send next question as JSON string
                                String nextQJson = objectMapper.writeValueAsString(toQuestionVO(ctx.nextQuestion));
                                emitter.send(SseEmitter.event().name("next_question").data(nextQJson));
                            } else {
                                emitter.send(SseEmitter.event().name("complete").data("面试已完成！"));
                            }

                            emitter.complete();
                        } catch (IOException e) {
                            log.error("SSE 发送完成事件失败", e);
                            emitter.completeWithError(e);
                        } catch (Exception e) {
                            log.error("SSE 事件序列化失败", e);
                            emitter.completeWithError(e);
                        }
                    })
                    .doOnError(error -> {
                        log.error("AI 流式生成反馈失败", error);
                        try {
                            String errorMessage;
                            if (error instanceof BusinessException be
                                && be.getCode() == ErrorCode.API_KEY_INSUFFICIENT_BALANCE) {
                                errorMessage = "你的 API Key 余额不足，请充值后继续使用。";
                            } else {
                                errorMessage = "抱歉，AI 反馈生成遇到问题，请稍后重试。";
                            }
                            emitter.send(SseEmitter.event().name("feedback_token").data(errorMessage));
                            emitter.send(SseEmitter.event().name("feedback_done")
                                .data("{\"feedback\":\"" + errorMessage + "\",\"score\":70}"));

                            if (ctx.nextQuestion != null) {
                                String nextQJson = objectMapper.writeValueAsString(toQuestionVO(ctx.nextQuestion));
                                emitter.send(SseEmitter.event().name("next_question").data(nextQJson));
                            } else {
                                emitter.send(SseEmitter.event().name("complete").data("面试已完成！"));
                            }
                            emitter.complete();
                        } catch (IOException e2) {
                            emitter.completeWithError(e2);
                        } catch (Exception e2) {
                            log.error("SSE 错误处理序列化异常", e2);
                            emitter.completeWithError(e2);
                        }
                    })
                    .doFinally(signal -> {
                        // 无论完成、出错还是取消，确保资源释放
                        // emitter.complete() 在已完成的 emitter 上调用是安全的（幂等）
                        try {
                            emitter.complete();
                        } catch (Exception ignored) {
                            // 已完成的 emitter 忽略
                        }
                    })
                    .subscribe();
            } catch (Exception e) {
                log.error("SSE 流处理异常", e);
                emitter.completeWithError(e);
            }
        }, sseTaskExecutor);

        return emitter;
    }

    /**
     * Save answer, advance to next question, and generate the next question (all in one transaction).
     */
    private AnswerContext saveAnswerAndAdvance(AnswerRequest request, Long userId) {
        InterviewSession session = sessionRepository.selectById(request.getSessionId());
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);
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
        question.setUpdatedAt(LocalDateTime.now());
        questionRepository.updateById(question);

        // Advance to next question and generate it
        boolean hasNext = sessionManager.advanceQuestion(session);
        sessionRepository.updateById(session);
        InterviewQuestion nextQuestion = null;
        if (hasNext) {
            int round = session.getCurrentRound();
            int questionIndex = session.getCurrentQuestion();
            nextQuestion = generateQuestion(session, round, questionIndex);
            nextQuestion.setSessionId(session.getId());
            questionRepository.insert(nextQuestion);
        }

        return new AnswerContext(session, question, nextQuestion);
    }

    /** Holds the result of the synchronous DB operations for the async SSE stream. */
    private record AnswerContext(InterviewSession session, InterviewQuestion question, InterviewQuestion nextQuestion) {}

    @Override
    @Transactional
    public StartRoundResponse skip(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);
        sessionManager.checkSessionActive(session);

        // Find current question
        int round = session.getCurrentRound();
        int questionIndex = session.getCurrentQuestion();
        List<InterviewQuestion> questions = questionRepository.findBySessionIdAndRound(sessionId, round);
        InterviewQuestion currentQuestion = questions.stream().filter(
                q -> q.getQuestionIndex().equals(questionIndex) && q.getStatus() == QuestionStatus.PENDING.getCode())
            .findFirst().orElse(null);

        if (currentQuestion != null) {
            currentQuestion.setStatus(QuestionStatus.SKIPPED.getCode());
            currentQuestion.setUpdatedAt(LocalDateTime.now());
            questionRepository.updateById(currentQuestion);
        }

        // Advance to next question
        boolean hasNext = sessionManager.advanceQuestion(session);
        sessionRepository.updateById(session);

        if (hasNext) {
            // Generate next question
            int nextRound = session.getCurrentRound();
            int nextIndex = session.getCurrentQuestion();
            InterviewQuestion nextQuestion = generateQuestion(session, nextRound, nextIndex);
            nextQuestion.setSessionId(session.getId());
            questionRepository.insert(nextQuestion);

            return StartRoundResponse.builder()
                .question(toQuestionVO(nextQuestion))
                .build();
        } else {
            // No more questions — session complete, return empty response
            return StartRoundResponse.builder()
                .question(null)
                .build();
        }
    }

    @Override
    @Transactional
    public void endSession(Long sessionId) {
        Long userId = authService.getCurrentUserEntity().getId();
        InterviewSession session = sessionRepository.selectById(sessionId);
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);

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
        BusinessException.checkOwnership(session != null && session.getUserId().equals(userId), BusinessException::interviewNotFound);

        List<InterviewQuestion> allQuestions = questionRepository.findBySessionId(sessionId);

        int totalQuestions = allQuestions.size();
        long answered = allQuestions.stream().filter(q -> q.getStatus() == QuestionStatus.ANSWERED.getCode()).count();
        long skipped = allQuestions.stream().filter(q -> q.getStatus() == QuestionStatus.SKIPPED.getCode()).count();

        List<InterviewSummaryVO.RoundSummary> roundSummaries = buildRoundSummaries(allQuestions);

        return InterviewSummaryVO.builder().sessionId(sessionId).totalRounds(sessionManager.getMaxRounds())
            .totalQuestions(totalQuestions).answeredQuestions((int)answered).skippedQuestions((int)skipped)
            .durationSeconds(session.getDurationSeconds() != null ? session.getDurationSeconds() : 0)
            .summary(session.getSummary()).rounds(roundSummaries).build();
    }

    // ========== Helper methods ==========

    private InterviewQuestion generateQuestion(InterviewSession session, int round, int questionIndex) {
        InterviewRound interviewRound = InterviewRound.fromCode(round);
        String questionText = generateAiQuestion(session, round, questionIndex, interviewRound);

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

    /**
     * Generate a question via AI using the candidate's resume and target position for context.
     */
    private String generateAiQuestion(InterviewSession session, int round, int questionIndex,
        InterviewRound interviewRound) {
        try {
            // Load resume and position data for context
            Resume resume = resumeRepository.selectById(session.getResumeId());
            TargetPosition position = session.getPositionId() != null
                ? positionRepository.selectById(session.getPositionId()) : null;

            String techStack = resume != null ? resume.getSummary() != null ? resume.getSummary() : "未提供" : "未提供";
            String workYears = "未知";
            String projectExp = "未提供";

            // Try to extract structured info from resume JSON fields
            if (resume != null && resume.getSkills() != null) {
                techStack = resume.getSkills();
            }
            if (resume != null && resume.getWorkExperience() != null) {
                projectExp = resume.getWorkExperience();
            }
            // Use parsed text as fallback context
            String resumeContext = resume != null && resume.getParsedText() != null
                ? resume.getParsedText().substring(0, Math.min(resume.getParsedText().length(), 500)) : "";

            String positionTitle = position != null ? position.getTitle() : "未知";
            String positionDesc = position != null ? position.getJdText() : "未知";

            // Build context from previous questions in this round to avoid duplicates
            List<InterviewQuestion> previousQuestions = questionRepository.findBySessionIdAndRound(session.getId(),
                round);
            String previousQuestionsText = previousQuestions.stream()
                .map(q -> String.format("第 %d 题: %s", q.getQuestionIndex(), q.getQuestionText()))
                .collect(Collectors.joining("\n"));

            String userPrompt = String.format(InterviewPrompt.USER_PROMPT_TEMPLATE,
                techStack.length() > 200 ? techStack.substring(0, 200) : techStack,
                workYears, projectExp.length() > 300 ? projectExp.substring(0, 300) : projectExp,
                positionTitle, positionDesc.length() > 500 ? positionDesc.substring(0, 500) : positionDesc,
                interviewRound.getName(), round, questionIndex);

            // Add previous questions context to avoid repetition
            String fullPrompt = userPrompt + "\n\n已提出的问题（请勿重复）:\n" + previousQuestionsText;

            // Use user's own API config if configured
            User user = authService.getCurrentUserEntity();
            String userApiKey = user.getApiKey();
            String userApiBaseUrl = user.getApiBaseUrl();
            String userApiModel = user.getApiModel();

            // RAG 策略（统一路径）：RetrievalAugmentationAdvisor 自动检索知识库（user_id 过滤隔离），
            // 根据简历技能栈+职位+轮次生成更有针对性的面试题；
            // 三种 key 配置行为一致（用户自备 key 时由 ApiKeyRoutingAdvisor 拦截模型调用）
            Map<String, Object> context = new HashMap<>();
            context.put("chat_memory_conversation_id", "interview-q-" + session.getId());
            context.put("vector_store_filter_expression", ragService.buildUserFilter(session.getUserId()));
            context.put("user_id", session.getUserId());
            if (userApiBaseUrl != null) {
                context.put("api_base_url", userApiBaseUrl);
            }
            if (userApiModel != null) {
                context.put("api_model", userApiModel);
            }

            String response = aiService.chat(InterviewPrompt.SYSTEM_PROMPT, fullPrompt, userApiKey, context);
            if (response != null && !response.isBlank()) {
                // Clean up — remove markdown code blocks and wrapping quotes
                String cleaned = response.trim();
                if (cleaned.startsWith("```")) {
                    int firstNewline = cleaned.indexOf('\n');
                    if (firstNewline > 0) {
                        cleaned = cleaned.substring(firstNewline).trim();
                    }
                }
                if (cleaned.endsWith("```")) {
                    cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
                }
                if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                    cleaned = cleaned.substring(1, cleaned.length() - 1);
                }
                log.info("AI 生成面试题成功: sessionId={}, round={}, questionIndex={}", session.getId(), round, questionIndex);
                return cleaned;
            }
        } catch (BusinessException e) {
            log.warn("AI 生成面试题失败: sessionId={}, round={}, questionIndex={}, code={}",
                session.getId(), round, questionIndex, e.getCode(), e);
            throw e; // 保留具体业务异常（如余额不足）向上传播
        } catch (Exception e) {
            log.warn("AI 生成面试题失败: sessionId={}, round={}, questionIndex={}",
                session.getId(), round, questionIndex, e);
        }

        throw BusinessException.aiServiceError("AI 生成面试题失败，返回内容为空");
    }

    private double parseScore(String text) {
        if (text == null) return 70.0;
        Matcher matcher = SCORE_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                log.warn("解析 SCORE 失败: {}", matcher.group(1));
            }
        }
        return 70.0;
    }

    private String escapeJson(String text) {
        if (text == null) return "\"\"";
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private List<InterviewSummaryVO.RoundSummary> buildRoundSummaries(List<InterviewQuestion> questions) {
        return questions.stream().collect(Collectors.groupingBy(InterviewQuestion::getRound)).entrySet().stream()
            .map(entry -> {
                int round = entry.getKey();
                List<InterviewQuestion> roundQuestions = entry.getValue();
                InterviewRound interviewRound = InterviewRound.fromCode(round);

                List<InterviewSummaryVO.QuestionSummary> questionSummaries = roundQuestions.stream().map(
                    q -> InterviewSummaryVO.QuestionSummary.builder().id(q.getId()).questionIndex(q.getQuestionIndex())
                        .questionText(q.getQuestionText()).userAnswer(q.getUserAnswer()).feedback(q.getFeedback())
                        .score(q.getScore() != null ? q.getScore().doubleValue() : null)
                        .status(QuestionStatus.fromCode(q.getStatus()).name()).build()).collect(Collectors.toList());

                return InterviewSummaryVO.RoundSummary.builder().round(round).roundName(interviewRound.getName())
                    .totalQuestions(roundQuestions.size()).answeredQuestions(
                        (int)roundQuestions.stream().filter(q -> q.getStatus() == QuestionStatus.ANSWERED.getCode())
                            .count()).skippedQuestions(
                        (int)roundQuestions.stream().filter(q -> q.getStatus() == QuestionStatus.SKIPPED.getCode())
                            .count()).questions(questionSummaries).build();
            }).collect(Collectors.toList());
    }

    private SessionVO toSessionVO(InterviewSession session) {
        return SessionVO.builder().id(session.getId()).resumeId(session.getResumeId())
            .positionId(session.getPositionId()).currentRound(session.getCurrentRound())
            .currentQuestion(session.getCurrentQuestion()).totalQuestions(session.getTotalQuestions())
            .status(session.getStatus()).durationSeconds(session.getDurationSeconds()).startedAt(session.getStartedAt())
            .finishedAt(session.getFinishedAt()).expiredAt(session.getExpiredAt()).createdAt(session.getCreatedAt())
            .build();
    }

    private InterviewQuestionVO toQuestionVO(InterviewQuestion question) {
        return InterviewQuestionVO.builder()
            .id(question.getId())
            .text(question.getQuestionText())
            .answer(question.getUserAnswer())
            .feedback(question.getFeedback())
            .score(question.getScore() != null ? question.getScore().doubleValue() : null)
            .status(QuestionStatus.fromCode(question.getStatus()).name())
            .build();
    }
}