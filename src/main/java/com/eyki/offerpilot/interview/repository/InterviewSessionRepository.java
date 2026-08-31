package com.eyki.offerpilot.interview.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.interview.domain.InterviewSession;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface InterviewSessionRepository extends BaseMapper<InterviewSession> {

    default List<InterviewSession> findByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId)
                .orderByDesc(InterviewSession::getCreatedAt));
    }

    default List<InterviewSession> findActiveByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<InterviewSession>()
                .eq(InterviewSession::getUserId, userId)
                .eq(InterviewSession::getStatus, 0) // IN_PROGRESS
                .orderByDesc(InterviewSession::getCreatedAt));
    }
}