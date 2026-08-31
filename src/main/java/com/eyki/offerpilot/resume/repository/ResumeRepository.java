package com.eyki.offerpilot.resume.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.resume.domain.Resume;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeRepository extends BaseMapper<Resume> {

    default List<Resume> findByUserId(Long userId) {
        return selectList(
            new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId).orderByDesc(Resume::getCreatedAt));
    }

    default long countByUserId(Long userId) {
        return selectCount(new LambdaQueryWrapper<Resume>().eq(Resume::getUserId, userId));
    }
}