package com.eyki.offerpilot.report.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.report.domain.Report;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReportRepository extends BaseMapper<Report> {

    default List<Report> findByUserId(Long userId) {
        return selectList(
            new LambdaQueryWrapper<Report>().eq(Report::getUserId, userId).orderByDesc(Report::getCreatedAt));
    }
}