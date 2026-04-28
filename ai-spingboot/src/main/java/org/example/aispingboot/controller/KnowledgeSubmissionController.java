package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.command.KnowledgeArticleCommandDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.KnowledgeArticle;
import org.example.aispingboot.service.KnowledgeArticleService;
import org.example.aispingboot.service.UserService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户投稿接口。
 * <p>
 * 路径刻意与 {@link KnowledgeArticleController} 的 {@code /api/knowledge/article/**} 区分开。
 * 原因是 SecurityConfig 中有一条公开规则 {@code GET /api/knowledge/article/*}，
 * 若把投稿/审核接口挂在那个前缀下，<b>很容易被这条规则意外放行，造成越权</b>。
 * 另起独立前缀可以从结构上避免这类事故。
 */
@RestController
@RequestMapping("/api/knowledge/submission")
public class KnowledgeSubmissionController {

    @Autowired
    private KnowledgeArticleService knowledgeArticleService;

    @Autowired
    private UserService userService;

    // 提交投稿（进入待审核）
    @PostMapping
    public Result<KnowledgeArticle> submit(@Valid @RequestBody KnowledgeArticleCommandDTO commandDTO) {
        Long userId = getCurrentUserId();
        String authorName = userService.getUserById(userId).getDisplayName();
        return Result.ok(knowledgeArticleService.submitByUser(commandDTO, userId, authorName));
    }

    // 我的投稿列表（含待审核/已驳回及驳回原因）
    @GetMapping("/mine")
    public Result<List<KnowledgeArticle>> mySubmissions() {
        return Result.ok(knowledgeArticleService.listMySubmissions(getCurrentUserId()));
    }

    // 删除自己的投稿（已发布的不可自行删除）
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeArticleService.deleteMySubmission(id, getCurrentUserId());
        return Result.ok(null);
    }

    private Long getCurrentUserId() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }
}
