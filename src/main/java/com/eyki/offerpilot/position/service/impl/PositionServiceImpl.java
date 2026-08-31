package com.eyki.offerpilot.position.service.impl;

import com.eyki.offerpilot.auth.service.AuthService;
import com.eyki.offerpilot.common.exception.BusinessException;
import com.eyki.offerpilot.position.domain.TargetPosition;
import com.eyki.offerpilot.position.dto.PositionRequest;
import com.eyki.offerpilot.position.dto.PositionVO;
import com.eyki.offerpilot.position.repository.PositionRepository;
import com.eyki.offerpilot.position.service.PositionService;
import com.eyki.offerpilot.resume.domain.Resume;
import com.eyki.offerpilot.resume.repository.ResumeRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionServiceImpl implements PositionService {

    private final PositionRepository positionRepository;
    private final ResumeRepository resumeRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public PositionVO create(PositionRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();

        // Verify resume belongs to user
        Resume resume = resumeRepository.selectById(request.getResumeId());
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
        }

        TargetPosition position = new TargetPosition();
        position.setUserId(userId);
        position.setResumeId(request.getResumeId());
        position.setTitle(request.getTitle());
        position.setCompany(request.getCompany());
        position.setJdText(request.getJdText());
        position.setLocation(request.getLocation());
        position.setSalaryRange(request.getSalaryRange());
        position.setCreatedAt(LocalDateTime.now());
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.insert(position);

        log.info("目标职位创建成功: userId={}, positionId={}, title={}", userId, position.getId(), position.getTitle());
        return toPositionVO(position);
    }

    @Override
    public PositionVO getDetail(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        TargetPosition position = positionRepository.selectById(id);
        if (position == null || !position.getUserId().equals(userId)) {
            throw BusinessException.positionNotFound();
        }
        return toPositionVO(position);
    }

    @Override
    @Transactional
    public PositionVO update(Long id, PositionRequest request) {
        Long userId = authService.getCurrentUserEntity().getId();
        TargetPosition position = positionRepository.selectById(id);
        if (position == null || !position.getUserId().equals(userId)) {
            throw BusinessException.positionNotFound();
        }

        // Verify resume belongs to user
        Resume resume = resumeRepository.selectById(request.getResumeId());
        if (resume == null || !resume.getUserId().equals(userId)) {
            throw BusinessException.resumeNotFound();
        }

        position.setResumeId(request.getResumeId());
        position.setTitle(request.getTitle());
        position.setCompany(request.getCompany());
        position.setJdText(request.getJdText());
        position.setLocation(request.getLocation());
        position.setSalaryRange(request.getSalaryRange());
        position.setUpdatedAt(LocalDateTime.now());
        positionRepository.updateById(position);

        log.info("目标职位更新成功: positionId={}", id);
        return toPositionVO(position);
    }

    @Override
    public List<PositionVO> listMyPositions() {
        Long userId = authService.getCurrentUserEntity().getId();
        return positionRepository.findByUserId(userId).stream().map(this::toPositionVO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        TargetPosition position = positionRepository.selectById(id);
        if (position == null || !position.getUserId().equals(userId)) {
            throw BusinessException.positionNotFound();
        }
        positionRepository.deleteById(id);
        log.info("目标职位删除成功: positionId={}", id);
    }

    @Override
    @Transactional
    public void setDefault(Long id) {
        Long userId = authService.getCurrentUserEntity().getId();
        TargetPosition position = positionRepository.selectById(id);
        if (position == null || !position.getUserId().equals(userId)) {
            throw BusinessException.positionNotFound();
        }

        // Clear existing default
        positionRepository.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TargetPosition>().eq(
                TargetPosition::getUserId, userId).eq(TargetPosition::getIsDefault, 1)).forEach(p -> {
            p.setIsDefault(0);
            positionRepository.updateById(p);
        });

        // Set new default
        position.setIsDefault(1);
        positionRepository.updateById(position);
        log.info("默认职位设置成功: positionId={}", id);
    }

    private PositionVO toPositionVO(TargetPosition position) {
        return PositionVO.builder().id(position.getId()).resumeId(position.getResumeId()).title(position.getTitle())
            .company(position.getCompany()).jdText(position.getJdText()).location(position.getLocation())
            .salaryRange(position.getSalaryRange()).isDefault(position.getIsDefault())
            .createdAt(position.getCreatedAt()).updatedAt(position.getUpdatedAt()).build();
    }
}