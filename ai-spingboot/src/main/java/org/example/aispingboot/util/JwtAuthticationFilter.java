package org.example.aispingboot.util;

import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.aispingboot.DTO.response.UserLoginResponseDTO;
import org.example.aispingboot.common.ResultCode;
import org.example.aispingboot.config.SecurityConfig;
import org.example.aispingboot.enumClass.UserStatus;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.TokenBlacklistService;
import org.example.aispingboot.service.UserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class JwtAuthticationFilter extends OncePerRequestFilter {
    @Resource
    private UserService userService;

    @Resource
    private TokenBlacklistService tokenBlacklistService;
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        // 检查是否为公开路径（区分请求方法）
        return SecurityConfig.isPublicPATH(request.getMethod(), requestUri);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        // 1. 提取 JWT token
        String token = JwtTokenUtil.extractTokenFromRequest(request);
        if (!StringUtils.hasText(token)) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.ACCESS_UNAUTHORIZED);
            return;
        }

        // 2. 校验token是否已登出（黑名单）
        if (tokenBlacklistService.isBlacklisted(token)) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_BLOCKED);
            return;
        }

        // 3. 验证token并获取用户信息
        // 注意：java-jwt 在 token 过期/签名错误时是「抛异常」而非返回 null，
        // 必须在此捕获，否则异常会穿透 Security 过滤器链变成 HTTP 500
        JwtTokenUtil.TokenVerificationResult validationResult;
        try {
            validationResult = JwtTokenUtil.validateToken(token);
        } catch (TokenExpiredException e) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_EXPIRED);
            return;
        } catch (JWTVerificationException e) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
            return;
        }
        if (validationResult == null || !validationResult.isValid()) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_INVALID);
            return;
        }

        // 4. 查询用户信息验证用户的状态
        // getUserById 在用户不存在时抛 BusinessException（如 token 未过期但账号已被删除）
        UserLoginResponseDTO.UserDetailResponseDTO user;
        try {
            user = userService.getUserById(validationResult.getUserId());
        } catch (BusinessException e) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_ACCESS_FORBIDDEN);
            return;
        }
        if (user == null || !UserStatus.NORMAL.getCode().equals(user.getStatus())) {
            clearSecurityContext();
            ResponseUtil.writeError(response, ResultCode.TOKEN_ACCESS_FORBIDDEN);
            return;
        }

        // 5. 创建Spring Security认证对象并写入上下文
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_" + validationResult.getRoleType())
        );

        // 创建UsernamePasswordAuthenticationToken对象
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                validationResult.getUsername(), // 用户名作为主体
                null,
                authorities
        );

        // 设置认证信息到Spring Securtity上下文
        SecurityContextHolder.getContext().setAuthentication(authentication);

        // 将token存储到请求属性中
        request.setAttribute("jwtToken", token);

        // 继续过滤器链
        chain.doFilter(request, response);
    }

    // 清理Spring Security上下文
    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
