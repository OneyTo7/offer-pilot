package com.eyki.offerpilot.resume.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.eyki.offerpilot.aicore.prompt.ResumeParsePrompt;
import com.eyki.offerpilot.aicore.service.AiService;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.service.ResumeParseService;
import com.eyki.offerpilot.storage.service.FileStorageService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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

            // 3. DeepSeek AI 结构化解析
            String aiResponse =
                aiService.chat(ResumeParsePrompt.getSystemPrompt(), ResumeParsePrompt.buildUserPrompt(rawText));

            // 4. 解析 AI 返回的 JSON
            parseAiResponse(resume, aiResponse);

            resume.setStatus(1); // COMPLETED
            resume.setUpdatedAt(LocalDateTime.now());
            log.info("简历解析完成: resumeId={}, name={}", resume.getId(), resume.getName());

        } catch (Exception e) {
            log.error("简历解析失败: resumeId={}", resume.getId(), e);
            resume.setStatus(2);
            resume.setFailReason("解析异常: " + e.getMessage());
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
     * 解析 AI 返回的 JSON 字符串，回写到 Resume 实体的各个 JSON 字段。
     */
    private void parseAiResponse(Resume resume, String aiResponse) {
        // 保存原始返回（调试用）
        resume.setRawResponse(aiResponse);

        try {
            // 清理 AI 返回：可能包含 markdown 代码块标记
            String cleaned = cleanJsonResponse(aiResponse);
            JSONObject root = JSONUtil.parseObj(cleaned);

            // 逐个字段提取
            if (root.containsKey("basic_info")) {
                resume.setBasicInfo(formatJsonField(root.get("basic_info")));
            }
            if (root.containsKey("education")) {
                resume.setEducation(formatJsonField(root.get("education")));
            }
            if (root.containsKey("work_experience")) {
                resume.setWorkExperience(formatJsonField(root.get("work_experience")));
            }
            if (root.containsKey("projects")) {
                resume.setProjects(formatJsonField(root.get("projects")));
            }
            if (root.containsKey("skills")) {
                resume.setSkills(formatJsonField(root.get("skills")));
            }
            if (root.containsKey("certificates")) {
                resume.setCertificates(formatJsonField(root.get("certificates")));
            }
            if (root.containsKey("summary")) {
                resume.setSummary(root.getStr("summary"));
            }

        } catch (Exception e) {
            log.warn("AI 响应 JSON 解析失败，保留 rawResponse 供调试: {}", e.getMessage());
        }
    }

    /**
     * 清理 AI 返回文本：去除 markdown 代码块标记等。
     */
    private String cleanJsonResponse(String text) {
        if (text == null) {
            return "{}";
        }
        String cleaned = text.trim();
        // 去除 ```json 和 ``` 标记
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }

    /**
     * 将对象格式化为紧凑的 JSON 字符串。
     */
    private String formatJsonField(Object obj) {
        if (obj == null) {
            return null;
        }
        return JSONUtil.toJsonStr(obj);
    }
}