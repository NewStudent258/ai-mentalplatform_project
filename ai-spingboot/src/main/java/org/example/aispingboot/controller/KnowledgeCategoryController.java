package org.example.aispingboot.controller;

import jakarta.annotation.Resource;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.KnowledgeCategory;
import org.example.aispingboot.service.KnowledgeCategoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/knowledge/category")
public class KnowledgeCategoryController {
    @Resource
    private KnowledgeCategoryService knowledgeCategoryService;

    // 获取分类列表
    @GetMapping("/tree")
    public Result<List<KnowledgeCategory>> tree() {
        return Result.ok(knowledgeCategoryService.tree());
    }
}
