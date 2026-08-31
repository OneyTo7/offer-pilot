package com.eyki.offerpilot.resume.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.common.model.ErrorCode;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.dto.ResumeDetailVO;
import com.eyki.offerpilot.resume.dto.ResumeVO;
import com.eyki.offerpilot.resume.enums.ResumeStatus;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import com.eyki.offerpilot.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeServiceImpl implements ResumeService {

    private final ResumeRepository resumeRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public ResumeVO upload(MultipartFile file, String name) {
        Long userId = authService.getCurrentUserEntity().getId();

        // TODO: Phase 7 — upload file to MinIO and get fileUrl
        // For now, stub the file URL
        String fileUrl = "stub://" + IdUtil.fastSimpleUUID() + "/" + (name != null ? name : file.getOriginalFilename());

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setName(name != null ? name : file.getOriginalFilename());
        resume.setFileUrl(fileUrl);
        resume.setFileSize((int) file.getSize());
        resume.setStatus(ResumeStatus.PARSING.getCode());
        resume.setCreatedAt(LocalDateTime.now());
        resume.setUpdatedAt(LocalDateTime.now());
        resumeRepository.insert(resume);

        // TODO: Phase 3/7 — async parsing via 百炼 API, update status when done
        // For now, mark as completed with stub data
        resume.setStatus(ResumeStatus.COMPLETED.getCode());
        resume.setParsedText("解析功能待接入 — 百炼 API 配置后生效");
        resume.setSummary("简历摘要待生成");
        resumeRepository.updateById(resume);

        log.info("简历上传成功: userId={}, resumeId={}, name={}", userId, resume.getId(), resume.getName());
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
        return resumeRepository.findByUserId(userId).stream()
                .map(this::toResumeVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        Resume resume = resumeRepository.selectById(id);
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
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
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Resume>()
                        .eq(Resume::getUserId, userId)
                        .eq(Resume::getIsDefault, 1)
        ).forEach(r -> {
            r.setIsDefault(0);
            resumeRepository.updateById(r);
        });

        // Set new default
        resume.setIsDefault(1);
        resumeRepository.updateById(resume);
        log.info("默认简历设置成功: resumeId={}", id);
    }

    private ResumeVO toResumeVO(Resume resume) {
        return ResumeVO.builder()
                .id(resume.getId())
                .name(resume.getName())
                .fileUrl(resume.getFileUrl())
                .fileSize(resume.getFileSize())
                .pageCount(resume.getPageCount())
                .status(resume.getStatus())
                .isDefault(resume.getIsDefault())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private ResumeDetailVO toResumeDetailVO(Resume resume) {
        // Parse tech_stack JSON string to List<String>
        List<String> techStack = null;
        if (resume.getTechStack() != null) {
            try {
                techStack = JSONUtil.parseArray(resume.getTechStack()).toList(String.class);
            } catch (Exception e) {
                log.warn("解析 tech_stack JSON 失败: {}", e.getMessage());
            }
        }

        return ResumeDetailVO.builder()
                .id(resume.getId())
                .name(resume.getName())
                .fileUrl(resume.getFileUrl())
                .fileSize(resume.getFileSize())
                .pageCount(resume.getPageCount())
                .parsedText(resume.getParsedText())
                .techStack(techStack)
                .workYears(resume.getWorkYears())
                .education(resume.getEducation())
                .summary(resume.getSummary())
                .isDefault(resume.getIsDefault())
                .status(resume.getStatus())
                .failReason(resume.getFailReason())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }
}