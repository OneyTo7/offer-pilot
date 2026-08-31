package com.eyki.offerpilot.aicore.memory.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.aicore.memory.domain.ChatMemoryMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatMemoryRecordRepository extends BaseMapper<ChatMemoryMessage> {
}