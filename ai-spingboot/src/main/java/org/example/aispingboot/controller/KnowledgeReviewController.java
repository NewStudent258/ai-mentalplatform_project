package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.command.ArticleReviewDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.KnowledgeArticleService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 投稿审核接口（仅管理员）。
 * <p>
 * 与投稿接口同理，使用独立前缀 {@code /api/knowledge/review}，
 * 避免被 SecurityConfig 中针对 {@code /api/knowledge/article/*} 的公开 GET 规则放行。
 */
@RestController
@RequestMapping("/api/knowledge/review")
public class KnowledgeReviewController {

    @Autowired
    private KnowledgeArticleService knowledgeArticleService;

    // 待审核队列
    @GetMapping("/pending")
    public Result<List<KnowledgeArticle>> pending() {
        checkAdmin();
        return Result.ok(knowledgeArticleService.listPendingReview());
    }

    // 审核：通过（并决定是否可被AI引用）或驳回（需填原因）
    @PostMapping("/{id}")
    public Result<KnowledgeArticle> review(@PathVariable Long id,
                                           @Valid @RequestBody ArticleReviewDTO reviewDTO) {
        Long reviewerId = checkAdmin();
        boolean approved = Boolean.TRUE.equals(reviewDTO.getApproved());
        // citable 缺省按 false 处理：授权 AI 引用必须是显式动作，不能靠默认值放开
        boolean citable = Boolean.TRUE.equals(reviewDTO.getCitable());
        return Result.ok(knowledgeArticleService.review(
                id, approved, citable, reviewDTO.getRejectReason(), reviewerId));
    }

    /**
     * 校验管理员身份并返回其用户ID。
     * <p>
     * 注意：roleType==2 代表「有管理权限」，并不等同于「具备心理专业资质」。
     * 在当前实现里审核权与管理员权限绑定；若后续要引入专业审核人角色，
     * 应在此处扩展为独立的审核权限，而非复用管理员角色。
     */
    private Long checkAdmin() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Integer roleType = jwt.getClaim("roleType").asInt();
        if (roleType == null || roleType != 2) {
            throw new BusinessException("无权限操作，仅管理员可审核投稿");
        }
        return jwt.getClaim("userId").asLong();
    }
}
