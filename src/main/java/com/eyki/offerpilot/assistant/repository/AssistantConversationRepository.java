package com.eyki.offerpilot.assistant.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.assistant.domain.AssistantConversation;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AssistantConversationRepository extends BaseMapper<AssistantConversation> {

    default List<AssistantConversation> findByUserId(Long userId) {
        return selectList(new LambdaQueryWrapper<AssistantConversation>()
            .eq(AssistantConversation::getUserId, userId)
            .eq(AssistantConversation::getStatus, "ACTIVE")
            .orderByDesc(AssistantConversation::getUpdatedAt));
    }
}