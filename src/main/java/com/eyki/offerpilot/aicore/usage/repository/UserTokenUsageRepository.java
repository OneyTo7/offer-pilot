package com.eyki.offerpilot.aicore.usage.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.aicore.usage.domain.UserTokenUsage;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTokenUsageRepository extends BaseMapper<UserTokenUsage> {

    default Optional<UserTokenUsage> findByUserIdAndDate(Long userId, LocalDate date) {
        return Optional.ofNullable(
            selectOne(new LambdaQueryWrapper<UserTokenUsage>()
                .eq(UserTokenUsage::getUserId, userId)
                .eq(UserTokenUsage::getUsageDate, date)));
    }

    default List<UserTokenUsage> findByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end) {
        return selectList(new LambdaQueryWrapper<UserTokenUsage>()
            .eq(UserTokenUsage::getUserId, userId)
            .between(UserTokenUsage::getUsageDate, start, end)
            .orderByAsc(UserTokenUsage::getUsageDate));
    }

    default List<UserTokenUsage> findByUserIdCurrentMonth(Long userId) {
        LocalDate now = LocalDate.now();
        LocalDate start = now.withDayOfMonth(1);
        return findByUserIdAndDateBetween(userId, start, now);
    }
}