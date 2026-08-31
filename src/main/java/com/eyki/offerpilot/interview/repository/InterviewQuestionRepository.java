package com.eyki.offerpilot.interview.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.interview.domain.InterviewQuestion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionRepository extends BaseMapper<InterviewQuestion> {

    default List<InterviewQuestion> findBySessionId(Long sessionId) {
        return selectList(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getSessionId, sessionId)
            .orderByAsc(InterviewQuestion::getRound, InterviewQuestion::getQuestionIndex));
    }

    default List<InterviewQuestion> findBySessionIdAndRound(Long sessionId, int round) {
        return selectList(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getSessionId, sessionId)
            .eq(InterviewQuestion::getRound, round).orderByAsc(InterviewQuestion::getQuestionIndex));
    }

    default long countBySessionId(Long sessionId) {
        return selectCount(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getSessionId, sessionId));
    }

    default long countBySessionIdAndRound(Long sessionId, int round) {
        return selectCount(new LambdaQueryWrapper<InterviewQuestion>().eq(InterviewQuestion::getSessionId, sessionId)
            .eq(InterviewQuestion::getRound, round));
    }
}