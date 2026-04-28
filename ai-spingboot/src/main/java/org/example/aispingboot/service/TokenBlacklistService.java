package org.example.aispingboot.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Token 黑名单（Redis 实现）：
 * 登出时把 token 加入黑名单，TTL = token 剩余有效期，实现"真注销"
 */
@Service
public class TokenBlacklistService {
    private static final String KEY_PREFIX = "auth:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 将 token 加入黑名单
     *
     * @param token      JWT 原文
     * @param ttlSeconds 过期时间（秒），应等于 token 剩余有效期
     */
    public void addToBlacklist(String token, long ttlSeconds) {
        if (token == null || token.isEmpty()) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofSeconds(Math.max(ttlSeconds, 1)));
    }

    /**
     * 判断 token 是否已被拉黑
     */
    public boolean isBlacklisted(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
