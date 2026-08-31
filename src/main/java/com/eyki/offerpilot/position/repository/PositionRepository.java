package com.eyki.offerpilot.position.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.position.domain.TargetPosition;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PositionRepository extends BaseMapper<TargetPosition> {

    default List<TargetPosition> findByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<TargetPosition>().eq(TargetPosition::getUserId, userId)
            .orderByDesc(TargetPosition::getCreatedAt));
    }

    default List<TargetPosition> findByResumeId(Long resumeId) {
        return selectList(new LambdaQueryWrapper<TargetPosition>().eq(TargetPosition::getResumeId, resumeId)
            .orderByDesc(TargetPosition::getCreatedAt));
    }
}