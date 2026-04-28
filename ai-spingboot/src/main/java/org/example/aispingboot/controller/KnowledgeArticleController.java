package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.aispingboot.DTO.command.KnowledgeArticleQueryDTO;
import org.example.aispingboot.DTO.command.KnowledgeArticleStatusDTO;
import org.example.aispingboot.common.PageResult;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.KnowledgeArticleService;
import org.example.aispingboot.service.UserService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge/article")
public class KnowledgeArticleController {
    @Resource
    private KnowledgeArticleService knowledgeArticleService;

    @Resource
    private UserService userService;

    // 分页查询文章（公开）
    @GetMapping("/page")
    public Result<PageResult<KnowledgeArticle>> page(KnowledgeArticleQueryDTO queryDTO) {
        return Result.ok(knowledgeArticleService.page(queryDTO));
    }

    // 文章详情（公开）
    @GetMapping("/{id}")
    public Result<KnowledgeArticle> detail(@PathVariable Long id) {
        return Result.ok(knowledgeArticleService.getDetail(id));
    }

    // 创建文章（管理员）
    @PostMapping
    public Result<KnowledgeArticle> create(@Valid @RequestBody KnowledgeArticleCommandDTO commandDTO) {
        checkAdmin();
        String authorName = userService.getUserById(getCurrentUserId()).getDisplayName();
        return Result.ok(knowledgeArticleService.create(commandDTO, authorName));
    }

    // 更新文章（管理员）
    @PutMapping("/{id}")
    public Result<KnowledgeArticle> update(@PathVariable Long id, @Valid @RequestBody KnowledgeArticleCommandDTO commandDTO) {
        checkAdmin();
        return Result.ok(knowledgeArticleService.update(id, commandDTO));
    }

    // 变更文章状态（管理员）
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody KnowledgeArticleStatusDTO statusDTO) {
        checkAdmin();
        knowledgeArticleService.updateStatus(id, statusDTO.getStatus());
        return Result.ok();
    }

    // 删除文章（管理员）
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        checkAdmin();
        knowledgeArticleService.delete(id);
        return Result.ok();
    }

    // 获取当前用户ID
    private Long getCurrentUserId() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }

    // 校验是否为管理员(用户类型2)
    private void checkAdmin() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Integer roleType = jwt.getClaim("roleType").asInt();
        if (roleType == null || roleType != 2) {
            throw new BusinessException("无权限操作，仅管理员可管理文章");
        }
    }
}
