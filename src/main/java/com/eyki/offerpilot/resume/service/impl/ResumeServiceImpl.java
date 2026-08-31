package com.eyki.offerpilot.resume.service.impl;

import cn.hutool.json.JSONUtil;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.dto.ResumeBasicInfo;
import com.eyki.offerpilot.resume.dto.ResumeCertificate;
import com.eyki.offerpilot.resume.dto.ResumeDetailVO;
import com.eyki.offerpilot.resume.dto.ResumeEducation;
import com.eyki.offerpilot.resume.dto.ResumeProject;
import com.eyki.offerpilot.resume.dto.ResumeSkill;
import com.eyki.offerpilot.resume.dto.ResumeVO;
import com.eyki.offerpilot.resume.dto.ResumeWorkExperience;
import com.eyki.offerpilot.resume.enums.ResumeStatus;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.eyki.offerpilot.resume.service.ResumeParseService;
import com.eyki.offerpilot.resume.service.ResumeService;
import com.eyki.offerpilot.storage.service.FileStorageService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final ResumeParseService resumeParseService;
    private final AuthService authService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public ResumeVO upload(MultipartFile file, String name) {
        Long userId = authService.getCurrentUserEntity().getId();

        // 仅允许 PDF 格式
        String fileName = name != null ? name : file.getOriginalFilename();
        validatePdf(file, fileName);

        // 上传到 MinIO
        String objectName = "resumes/" + userId + "/" + System.currentTimeMillis() + "_" + sanitizeFileName(fileName);
        String fileUrl;
        try {
            fileUrl = fileStorageService.upload(objectName, file.getInputStream(), "application/pdf");
        } catch (IOException e) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "文件读取失败: " + e.getMessage());
        }

        // 创建实体
        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(fileName);
        resume.setFileUrl(objectName);
        resume.setFileSize((int)file.getSize());
        resume.setStatus(ResumeStatus.PARSING.getCode());
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());
        resumeRepository.insert(resume);

        // 异步解析
        Long resumeId = resume.getId();
        CompletableFuture.runAsync(() -> {
            try {
                Resume parsed = resumeParseService.parse(resumeRepository.selectById(resumeId));
                resumeRepository.updateById(parsed);
                log.info("简历异步解析完成: resumeId={}", resumeId);
            } catch (Exception e) {
                log.error("简历异步解析失败: resumeId={}", resumeId, e);
                Resume failed = resumeRepository.selectById(resumeId);
                if (failed != null) {
                    failed.setStatus(ResumeStatus.FAILED.getCode());
                    failed.setFailReason("解析异常: " + e.getMessage());
                    failed.setUpdatedAt(LocalDateTime.now());
                    resumeRepository.updateById(failed);
                }
            }
        });

        log.info("简历上传成功: userId={}, resumeId={}, name={}", userId, resume.getId(), fileName);
        return toResumeVO(resume);
    }

    @Override
    public ResumeDetailVO getDetail(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Resume resume = resumeRepository.selectById(id);
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
        }
        return toResumeDetailVO(resume);
    }

    @Override
    public List<ResumeVO> listMyResumes() {
        Long userId = authService.getCurrentUserEntity().getId();
        return resumeRepository.findByUserId(userId).stream().map(this::toResumeVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Resume resume = resumeRepository.selectById(id);
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
        }

        // 删除 MinIO 文件
        if (resume.getFileUrl() != null) {
            try {
                fileStorageService.delete(resume.getFileUrl());
            } catch (Exception e) {
                log.warn("删除 MinIO 文件失败: {}", resume.getFileUrl(), e);
            }
        }

        resumeRepository.deleteById(id);
        log.info("简历删除成功: resumeId={}", id);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Resume resume = resumeRepository.selectById(id);
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
        }

        // Clear existing default
        resumeRepository.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resume>().eq(Resume::getUserId,
                userId).eq(Resume::getIsDefault, 1)).forEach(r -> {
            r.setIsDefault(0);
            resumeRepository.updateById(r);
        });

        // Set new default
        resume.setIsDefault(1);
        resumeRepository.updateById(resume);
        log.info("默认简历设置成功: resumeId={}", id);
    }

    // ========== 内部方法 ==========

    /**
     * 校验文件是否为 PDF。
     */
    private void validatePdf(MultipartFile file, String fileName) {
        // 检查文件名后缀
        String lowerName = fileName != null ? fileName.toLowerCase() : "";
        if (!lowerName.endsWith(".pdf")) {
            throw BusinessException.badRequest("仅支持 PDF 格式的简历文件");
        }
        // 检查 Content-Type
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equals("application/pdf")) {
            throw BusinessException.badRequest("仅支持 PDF 格式的简历文件");
        }
    }

    /**
     * 清理文件名：只保留安全字符。
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "resume.pdf";
        }
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private ResumeVO toResumeVO(Resume resume) {
        return ResumeVO.builder().id(resume.getId()).name(resume.getName()).fileUrl(resume.getFileUrl())
            .fileSize(resume.getFileSize()).pageCount(resume.getPageCount()).status(resume.getStatus())
            .isDefault(resume.getIsDefault()).createdAt(resume.getCreatedAt()).updatedAt(resume.getUpdatedAt()).build();
    }

    private ResumeDetailVO toResumeDetailVO(Resume resume) {
        var builder = ResumeDetailVO.builder().id(resume.getId()).name(resume.getName()).fileUrl(resume.getFileUrl())
            .fileSize(resume.getFileSize()).pageCount(resume.getPageCount()).parsedText(resume.getParsedText())
            .summary(resume.getSummary()).isDefault(resume.getIsDefault()).status(resume.getStatus())
            .failReason(resume.getFailReason()).createdAt(resume.getCreatedAt()).updatedAt(resume.getUpdatedAt());

        // JSON 字符串 → Java 对象
        if (resume.getBasicInfo() != null) {
            try {
                builder.basicInfo(JSONUtil.toBean(resume.getBasicInfo(), ResumeBasicInfo.class));
            } catch (Exception e) {
                log.warn("解析 basic_info JSON 失败: {}", e.getMessage());
            }
        }
        if (resume.getEducation() != null) {
            try {
                builder.education(JSONUtil.parseArray(resume.getEducation()).toList(ResumeEducation.class));
            } catch (Exception e) {
                log.warn("解析 education JSON 失败: {}", e.getMessage());
            }
        }
        if (resume.getWorkExperience() != null) {
            try {
                builder.workExperience(
                    JSONUtil.parseArray(resume.getWorkExperience()).toList(ResumeWorkExperience.class));
            } catch (Exception e) {
                log.warn("解析 work_experience JSON 失败: {}", e.getMessage());
            }
        }
        if (resume.getProjects() != null) {
            try {
                builder.projects(JSONUtil.parseArray(resume.getProjects()).toList(ResumeProject.class));
            } catch (Exception e) {
                log.warn("解析 projects JSON 失败: {}", e.getMessage());
            }
        }
        if (resume.getSkills() != null) {
            try {
                builder.skills(JSONUtil.parseArray(resume.getSkills()).toList(ResumeSkill.class));
            } catch (Exception e) {
                log.warn("解析 skills JSON 失败: {}", e.getMessage());
            }
        }
        if (resume.getCertificates() != null) {
            try {
                builder.certificates(JSONUtil.parseArray(resume.getCertificates()).toList(ResumeCertificate.class));
            } catch (Exception e) {
                log.warn("解析 certificates JSON 失败: {}", e.getMessage());
            }
        }

        return builder.build();
    }
}