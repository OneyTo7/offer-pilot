package com.eyki.offerpilot.resume.service.impl;

import com.eyki.offerpilot.aicore.prompt.ResumeParsePrompt;
import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.dto.ResumeParseResult;
import com.eyki.offerpilot.resume.service.ResumeParseService;
import com.eyki.offerpilot.storage.service.FileStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * 简历解析服务实现。
 *
 * 解析管线： 1. PDFBox 提取文本（文本型 PDF） 2. 若文本过短 ⇒ Tess4j OCR 降级（扫描件） 3. DeepSeek AI 结构化解析 4. 结果回写到 Resume 实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeParseServiceImpl implements ResumeParseService {

    private static final int MIN_TEXT_LENGTH = 50; // 少于 50 字符认为提取失败，触发 OCR 降级

    private final AiService aiService;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    @Override
    public Resume parse(Resume resume) {
        String filePath = resume.getFileUrl();
        if (filePath == null || filePath.isBlank()) {
            resume.setStatus(2);
            resume.setFailReason("文件路径为空");
            resume.setUpdatedAt(LocalDateTime.now());
            return resume;
        }

        // 从 MinIO 下载到临时文件
        File tempFile = downloadFromMinIO(filePath);
        if (tempFile == null) {
            resume.setStatus(2);
            resume.setFailReason("文件不存在: " + filePath);
            resume.setUpdatedAt(LocalDateTime.now());
            return resume;
        }

        try {
            // 记录文件大小
            resume.setFileSize((int)tempFile.length());

            // 1. PDFBox 提取文本
            String rawText = extractTextWithPdfBox(tempFile);
            resume.setPageCount(countPages(tempFile));

            // 2. 如果文本过短，尝试 OCR 降级
            if (rawText == null || rawText.length() < MIN_TEXT_LENGTH) {
                log.info("PDFBox 提取文本过短 ({} 字符)，尝试 Tess4j OCR", rawText != null ? rawText.length() : 0);
                rawText = extractTextWithOcr(tempFile);
            }

            if (rawText == null || rawText.isBlank()) {
                resume.setStatus(2);
                resume.setFailReason("无法提取简历文本内容（非文本 PDF 且 OCR 失败）");
                resume.setUpdatedAt(LocalDateTime.now());
                return resume;
            }

            resume.setParsedText(rawText);
            log.info("简历文本提取成功: filePath={}, textLength={}字符", filePath, rawText.length());

            // 3. DeepSeek AI 结构化解析。
            //    走无 RAG 的 ChatClient：简历解析是纯提取场景，知识库内容注入会污染解析结果
            //    （chatWithEntity 会经 RetrievalAugmentationAdvisor 自动检索知识库）
            //    context 传 user_id 激活 TokenUsageAdvisor：前置额度校验 + 后置用量累计
            ResumeParseResult result = aiService.chatWithEntityNoRag(
                ResumeParsePrompt.getSystemPrompt(),
                ResumeParsePrompt.buildUserPrompt(rawText),
                ResumeParseResult.class,
                Map.of("user_id", resume.getUserId()));

            // 4. 将解析结果回写到 Resume 实体
            parseAiResponse(resume, result);

            resume.setStatus(1); // COMPLETED
            resume.setUpdatedAt(LocalDateTime.now());
            log.info("简历解析完成: resumeId={}, name={}", resume.getId(), resume.getName());

        } catch (Exception e) {
            log.error("简历解析失败: resumeId={}", resume.getId(), e);
            resume.setStatus(2);
            // 业务异常（如 token 额度不足 429）直接透出业务消息，其余加前缀
            resume.setFailReason(
                e instanceof BusinessException be ? be.getMessage() : "解析异常: " + e.getMessage());
            resume.setUpdatedAt(LocalDateTime.now());
        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }

        return resume;
    }

    // ========== MinIO 文件下载 ==========

    /**
     * 从 MinIO 下载文件到临时目录。
     */
    private File downloadFromMinIO(String objectName) {
        try {
            InputStream inputStream = fileStorageService.download(objectName);
            Path tempFile = Files.createTempFile("resume-", ".pdf");
            Files.copy(inputStream, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            inputStream.close();
            log.debug("文件已从 MinIO 下载到临时文件: {}", tempFile);
            return tempFile.toFile();
        } catch (Exception e) {
            log.warn("从 MinIO 下载文件失败: objectName={}", objectName, e);
            return null;
        }
    }

    // ========== PDFBox 文本提取 ==========

    private String extractTextWithPdfBox(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        } catch (IOException e) {
            log.warn("PDFBox 提取失败: {}", e.getMessage());
            return "";
        }
    }

    private int countPages(File file) {
        try (PDDocument document = Loader.loadPDF(file)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            log.warn("获取 PDF 页数失败: {}", e.getMessage());
            return 0;
        }
    }

    // ========== Tess4j OCR 降级 ==========

    private String extractTextWithOcr(File file) {
        try {
            // Tess4j 5.x API
            net.sourceforge.tess4j.Tesseract tesseract = new net.sourceforge.tess4j.Tesseract();
            // 设置语言为中文 + 英文
            tesseract.setLanguage("chi_sim+eng");
            // 设置 TESSDATA 路径（优先使用环境变量，否则用默认路径）
            String tessdataPath = System.getenv("TESSDATA_PREFIX");
            if (tessdataPath != null) {
                tesseract.setDatapath(tessdataPath);
            }
            // 设置 OCR 引擎模式为 LSTM_ONLY（更快更准）
            tesseract.setOcrEngineMode(1); // LSTM_ONLY

            String result = tesseract.doOCR(file);
            return result != null ? result.trim() : "";
        } catch (Exception e) {
            log.warn("Tess4j OCR 识别失败: {}", e.getMessage());
            return "";
        }
    }

    // ========== AI 响应解析 ==========

    /**
     * 将 AI 解析结果回写到 Resume 实体的各个 JSON 字段。
     */
    private void parseAiResponse(Resume resume, ResumeParseResult result) {
        try {
            // 保存原始返回（调试用）
            resume.setRawResponse(objectMapper.writeValueAsString(result));

            // 逐个字段序列化为 JSON 字符串存入实体
            if (result.basicInfo() != null) {
                resume.setBasicInfo(objectMapper.writeValueAsString(result.basicInfo()));
            }
            if (result.education() != null) {
                resume.setEducation(objectMapper.writeValueAsString(result.education()));
            }
            if (result.workExperience() != null) {
                resume.setWorkExperience(objectMapper.writeValueAsString(result.workExperience()));
            }
            if (result.projects() != null) {
                resume.setProjects(objectMapper.writeValueAsString(result.projects()));
            }
            if (result.skills() != null) {
                resume.setSkills(objectMapper.writeValueAsString(result.skills()));
            }
            if (result.certificates() != null) {
                resume.setCertificates(objectMapper.writeValueAsString(result.certificates()));
            }
            if (result.summary() != null) {
                resume.setSummary(result.summary());
            }

        } catch (Exception e) {
            log.warn("AI 响应 JSON 序列化失败: {}", e.getMessage());
        }
    }
}