package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.DataAnalyticsService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理后台数据分析接口（仅管理员）。
 */
@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Autowired
    private DataAnalyticsService dataAnalyticsService;

    // 首页概览：系统指标 + 咨询统计 + 情绪趋势 + 用户活跃度
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        checkAdmin();
        return Result.ok(dataAnalyticsService.overview());
    }

    private void checkAdmin() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Integer roleType = jwt.getClaim("roleType").asInt();
        if (roleType == null || roleType != 2) {
            throw new BusinessException("无权限操作，仅管理员可查看数据分析");
        }
    }
}
