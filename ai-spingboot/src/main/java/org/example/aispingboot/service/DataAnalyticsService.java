package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.entity.ConsultationMessage;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.enumClass.UserStatus;
import org.example.aispingboot.mapper.ConsultationMessageMapper;
import org.example.aispingboot.mapper.ConsultationSessionMapper;
import org.example.aispingboot.mapper.EmotionDiaryMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理后台数据分析。
 * <p>
 * 统计口径说明（避免「数字对不上」的争议，这些定义需要明确写下来）：
 * <ul>
 *   <li><b>活跃用户</b>：状态为「正常」的账号数。而非「最近登录过」——
 *       当前系统没有记录登录时间，用状态近似，这是一个已知的简化。</li>
 *   <li><b>会话时长</b>：该会话最后一条消息时间 − 会话开始时间。
 *       项目未记录会话结束事件，用最后一条消息近似会话结束时刻。</li>
 *   <li><b>趋势类数据</b>：固定返回最近 7 天，且<b>补齐没有数据的日期</b>，
 *       否则折线图会把空缺的日子直接跳过，造成时间轴失真。</li>
 * </ul>
 */
@Service
public class DataAnalyticsService {

    /** 趋势数据的天数窗口 */
    private static final int TREND_DAYS = 7;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmotionDiaryMapper emotionDiaryMapper;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    /**
     * 汇总后台首页所需的全部数据。
     * <p>
     * 一次性返回而非拆成多个接口：首页会同时用到这四块数据，
     * 拆开会让前端发起 4 个请求、且各块数据存在时间差，图表之间对不齐。
     */
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("systemOverview", buildSystemOverview());
        result.put("consultationStats", buildConsultationStats());
        result.put("emotionTrend", buildEmotionTrend());
        result.put("userActivity", buildUserActivity());
        return result;
    }

    /** 顶部四张卡片的汇总指标 */
    private Map<String, Object> buildSystemOverview() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        Map<String, Object> overview = new LinkedHashMap<>();

        Long totalUsers = userMapper.selectCount(null);
        // 活跃用户 = 状态正常的账号（见类注释中的口径说明）
        Long activeUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, UserStatus.NORMAL.getCode()));

        overview.put("totalUsers", totalUsers);
        overview.put("activeUsers", activeUsers);

        overview.put("totalDiaries", emotionDiaryMapper.selectCount(null));
        overview.put("todayNewDiaries", emotionDiaryMapper.selectCount(
                new LambdaQueryWrapper<EmotionDiary>().ge(EmotionDiary::getCreatedAt, todayStart)));

        overview.put("totalSessions", consultationSessionMapper.selectCount(null));
        overview.put("todayNewSessions", consultationSessionMapper.selectCount(
                new LambdaQueryWrapper<ConsultationSession>().ge(ConsultationSession::getStartedAt, todayStart)));

        overview.put("avgMoodScore", averageMoodScore());
        return overview;
    }

    /**
     * 平均情绪分。
     * <p>
     * 无数据时返回 0 而不是 null：前端模板直接渲染该值并拼上 "/10"，
     * 返回 null 会显示成 "null/10"。
     */
    private double averageMoodScore() {
        // 注意：这里刻意不使用 select(...) 投影。本项目使用 spring-boot-starter-data-jdbc
        // 的同时又引入 MyBatis-Plus，部分列投影在映射 DATETIME 列时会抛
        // "Unsupported conversion from DATETIME to java.lang.Long"。
        // 查询全字段可绕开该问题，且当前数据量下没有性能顾虑。
        List<EmotionDiary> diaries = emotionDiaryMapper.selectList(null);
        return diaries.stream()
                .filter(d -> d.getMoodScore() != null)
                .mapToInt(EmotionDiary::getMoodScore)
                .average()
                // 保留一位小数，避免前端出现一长串小数位
                .stream().map(v -> Math.round(v * 10) / 10.0).findFirst().orElse(0.0);
    }

    /** 咨询统计：总量、平均时长、近 7 天趋势 */
    private Map<String, Object> buildConsultationStats() {
        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();

        // 只取窗口期内的会话，用于趋势与时长计算
        List<ConsultationSession> recentSessions = consultationSessionMapper.selectList(
                new LambdaQueryWrapper<ConsultationSession>()
                        .ge(ConsultationSession::getStartedAt, windowStart));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSessions", consultationSessionMapper.selectCount(null));
        stats.put("avgDurationMinutes", averageSessionMinutes(recentSessions));

        // 按日期聚合会话数与去重用户数
        Map<String, Integer> sessionCountByDate = new HashMap<>();
        Map<String, Set<Long>> usersByDate = new HashMap<>();
        for (ConsultationSession session : recentSessions) {
            if (session.getStartedAt() == null) {
                continue;
            }
            String date = session.getStartedAt().toLocalDate().format(DATE_FORMAT);
            sessionCountByDate.merge(date, 1, Integer::sum);
            if (session.getUserId() != null) {
                usersByDate.computeIfAbsent(date, k -> new java.util.HashSet<>()).add(session.getUserId());
            }
        }

        List<Map<String, Object>> dailyTrend = new ArrayList<>();
        for (LocalDate date : lastDays(today)) {
            String key = date.format(DATE_FORMAT);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("sessionCount", sessionCountByDate.getOrDefault(key, 0));
            point.put("userCount", usersByDate.getOrDefault(key, Set.of()).size());
            dailyTrend.add(point);
        }
        stats.put("dailyTrend", dailyTrend);
        return stats;
    }

    /**
     * 会话平均时长（分钟）。
     * <p>
     * 一次查出窗口内所有会话的消息，在内存里按会话分组取最后一条时间，
     * 避免对每个会话单独查一次数据库（N+1 查询）。
     */
    private long averageSessionMinutes(List<ConsultationSession> sessions) {
        if (sessions.isEmpty()) {
            return 0;
        }
        List<Long> sessionIds = sessions.stream()
                .map(ConsultationSession::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        if (sessionIds.isEmpty()) {
            return 0;
        }

        List<ConsultationMessage> messages = consultationMessageMapper.selectList(
                new LambdaQueryWrapper<ConsultationMessage>()
                        .in(ConsultationMessage::getSessionId, sessionIds));

        Map<Long, LocalDateTime> lastMessageAt = new HashMap<>();
        for (ConsultationMessage message : messages) {
            if (message.getSessionId() == null || message.getCreatedAt() == null) {
                continue;
            }
            lastMessageAt.merge(message.getSessionId(), message.getCreatedAt(),
                    (a, b) -> a.isAfter(b) ? a : b);
        }

        long totalMinutes = 0;
        int counted = 0;
        for (ConsultationSession session : sessions) {
            LocalDateTime last = lastMessageAt.get(session.getId());
            if (last == null || session.getStartedAt() == null) {
                continue;
            }
            long minutes = Duration.between(session.getStartedAt(), last).toMinutes();
            if (minutes >= 0) {
                totalMinutes += minutes;
                counted++;
            }
        }
        return counted == 0 ? 0 : totalMinutes / counted;
    }

    /** 情绪趋势：近 7 天平均心情分与记录条数 */
    private List<Map<String, Object>> buildEmotionTrend() {
        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();

        List<EmotionDiary> diaries = emotionDiaryMapper.selectList(
                new LambdaQueryWrapper<EmotionDiary>()
                        .ge(EmotionDiary::getCreatedAt, windowStart));

        Map<String, List<Integer>> scoresByDate = new HashMap<>();
        for (EmotionDiary diary : diaries) {
            if (diary.getCreatedAt() == null || diary.getMoodScore() == null) {
                continue;
            }
            String date = diary.getCreatedAt().toLocalDate().format(DATE_FORMAT);
            scoresByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(diary.getMoodScore());
        }

        List<Map<String, Object>> trend = new ArrayList<>();
        for (LocalDate date : lastDays(today)) {
            String key = date.format(DATE_FORMAT);
            List<Integer> scores = scoresByDate.getOrDefault(key, List.of());
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("recordCount", scores.size());
            // 当天没有记录时给 0，折线图会如实显示为断点/低谷，而不是伪造一个平均值
            double avg = scores.isEmpty() ? 0.0
                    : Math.round(scores.stream().mapToInt(Integer::intValue).average().orElse(0.0) * 10) / 10.0;
            point.put("avgMoodScore", avg);
            trend.add(point);
        }
        return trend;
    }

    /** 用户活跃度趋势：近 7 天的新增用户、写过日志的用户、活跃用户 */
    private List<Map<String, Object>> buildUserActivity() {
        LocalDate today = LocalDate.now();
        LocalDateTime windowStart = today.minusDays(TREND_DAYS - 1L).atStartOfDay();

        List<User> newUsers = userMapper.selectList(
                new LambdaQueryWrapper<User>().ge(User::getCreatedAt, windowStart));
        List<EmotionDiary> diaries = emotionDiaryMapper.selectList(
                new LambdaQueryWrapper<EmotionDiary>().ge(EmotionDiary::getCreatedAt, windowStart));
        List<ConsultationSession> sessions = consultationSessionMapper.selectList(
                new LambdaQueryWrapper<ConsultationSession>().ge(ConsultationSession::getStartedAt, windowStart));

        Map<String, Integer> newUserByDate = new HashMap<>();
        for (User user : newUsers) {
            if (user.getCreatedAt() == null) {
                continue;
            }
            newUserByDate.merge(user.getCreatedAt().toLocalDate().format(DATE_FORMAT), 1, Integer::sum);
        }

        Map<String, Set<Long>> diaryUsersByDate = new HashMap<>();
        for (EmotionDiary diary : diaries) {
            if (diary.getCreatedAt() == null || diary.getUserId() == null) {
                continue;
            }
            diaryUsersByDate.computeIfAbsent(diary.getCreatedAt().toLocalDate().format(DATE_FORMAT),
                    k -> new java.util.HashSet<>()).add(diary.getUserId());
        }

        // 活跃用户 = 当天有过咨询会话或写过情绪日志的用户（去重）
        Map<String, Set<Long>> activeByDate = new HashMap<>();
        for (ConsultationSession session : sessions) {
            if (session.getStartedAt() == null || session.getUserId() == null) {
                continue;
            }
            activeByDate.computeIfAbsent(session.getStartedAt().toLocalDate().format(DATE_FORMAT),
                    k -> new java.util.HashSet<>()).add(session.getUserId());
        }
        diaryUsersByDate.forEach((date, users) ->
                activeByDate.computeIfAbsent(date, k -> new java.util.HashSet<>()).addAll(users));

        List<Map<String, Object>> activity = new ArrayList<>();
        for (LocalDate date : lastDays(today)) {
            String key = date.format(DATE_FORMAT);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", key);
            point.put("newUsers", newUserByDate.getOrDefault(key, 0));
            point.put("diaryUsers", diaryUsersByDate.getOrDefault(key, Set.of()).size());
            point.put("activeUsers", activeByDate.getOrDefault(key, Set.of()).size());
            activity.add(point);
        }
        return activity;
    }

    /** 返回含今天在内的最近 N 天，按时间正序 */
    private List<LocalDate> lastDays(LocalDate today) {
        List<LocalDate> dates = new ArrayList<>(TREND_DAYS);
        for (int i = TREND_DAYS - 1; i >= 0; i--) {
            dates.add(today.minusDays(i));
        }
        return dates;
    }
}
