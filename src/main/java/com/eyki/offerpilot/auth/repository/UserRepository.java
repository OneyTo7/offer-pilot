package com.eyki.offerpilot.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.auth.domain.User;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRepository extends BaseMapper<User> {

    default Optional<User> findByEmail(String email) {
        User user = selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        return Optional.ofNullable(user);
    }

    default boolean existsByEmail(String email) {
        return selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0;
    }
}