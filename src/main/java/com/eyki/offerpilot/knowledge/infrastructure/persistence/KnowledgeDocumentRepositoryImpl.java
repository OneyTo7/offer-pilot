package com.eyki.offerpilot.knowledge.infrastructure.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocument;
import com.eyki.offerpilot.knowledge.domain.KnowledgeDocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Component;

/**
 * 知识文档 Repository — MyBatis-Plus 实现。
 *
 * 领域层 {@link KnowledgeDocumentRepository} 接口的唯一实现。 负责 PO ⇔ Domain 转换，封装所有 MyBatis-Plus 调用细节。
 */
@Component
public class KnowledgeDocumentRepositoryImpl implements KnowledgeDocumentRepository {

    private final KnowledgeDocumentMapper mapper;
    private final KnowledgeDocumentConverter converter;

    public KnowledgeDocumentRepositoryImpl(KnowledgeDocumentMapper mapper, KnowledgeDocumentConverter converter) {
        this.mapper = mapper;
        this.converter = converter;
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        KnowledgeDocumentPO po = converter.toPO(document);
        if (document.getId() == null) {
            // 新增
            mapper.insert(po);
            document.onPersisted(po.getId());
        } else {
            // 更新
            mapper.updateById(po);
        }
        return document;
    }

    @Override
    public Optional<KnowledgeDocument> findById(Long id) {
        KnowledgeDocumentPO po = mapper.selectById(id);
        return Optional.ofNullable(po).map(converter::toDomain);
    }

    @Override
    public Optional<KnowledgeDocument> findByUserIdAndId(Long userId, Long id) {
        LambdaQueryWrapper<KnowledgeDocumentPO> wrapper =
            new LambdaQueryWrapper<KnowledgeDocumentPO>().eq(KnowledgeDocumentPO::getUserId, userId)
                .eq(KnowledgeDocumentPO::getId, id);
        KnowledgeDocumentPO po = mapper.selectOne(wrapper);
        return Optional.ofNullable(po).map(converter::toDomain);
    }

    @Override
    public List<KnowledgeDocument> findByUserId(Long userId) {
        LambdaQueryWrapper<KnowledgeDocumentPO> wrapper =
            new LambdaQueryWrapper<KnowledgeDocumentPO>().eq(KnowledgeDocumentPO::getUserId, userId)
                .orderByDesc(KnowledgeDocumentPO::getCreatedAt);
        return mapper.selectList(wrapper).stream().map(converter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeDocument> findByScope(String scope) {
        LambdaQueryWrapper<KnowledgeDocumentPO> wrapper =
            new LambdaQueryWrapper<KnowledgeDocumentPO>().eq(KnowledgeDocumentPO::getScope, scope)
                .orderByDesc(KnowledgeDocumentPO::getCreatedAt);
        return mapper.selectList(wrapper).stream().map(converter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeDocument> findByUserIdAndScope(Long userId, String scope) {
        LambdaQueryWrapper<KnowledgeDocumentPO> wrapper =
            new LambdaQueryWrapper<KnowledgeDocumentPO>().eq(KnowledgeDocumentPO::getUserId, userId)
                .eq(KnowledgeDocumentPO::getScope, scope)
                .orderByDesc(KnowledgeDocumentPO::getCreatedAt);
        return mapper.selectList(wrapper).stream().map(converter::toDomain).collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    /**
     * MyBatis-Plus Mapper（包级私有，仅 RepositoryImpl 使用）。
     */
    @Mapper
    interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentPO> {
    }
}