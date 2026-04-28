package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.mapper.KnowledgeCategoryMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeCategoryService {
    @Resource
    private KnowledgeCategoryMapper knowledgeCategoryMapper;

    /**
     * 查询启用的分类列表（按排序号升序）
     * 分类数据低频变更，命中 Redis 缓存（10分钟TTL）
     */
    @Cacheable(value = "categoryTree")
    public List<KnowledgeCategory> tree() {
        LambdaQueryWrapper<KnowledgeCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeCategory::getStatus, 1)
                .orderByAsc(KnowledgeCategory::getSortOrder)
                .orderByAsc(KnowledgeCategory::getId);
        return knowledgeCategoryMapper.selectList(wrapper);
    }
}
