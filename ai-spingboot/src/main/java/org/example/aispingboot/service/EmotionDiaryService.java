package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import org.example.aispingboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.aispingboot.common.PageResult;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.mapper.EmotionDiaryMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class EmotionDiaryService {
    @Resource
    private EmotionDiaryMapper emotionDiaryMapper;

    @Resource
    private UserMapper userMapper;

    /**
     * 创建情绪日记记录
     */
    public EmotionDiary createDiary(Long userId, EmotionDiaryCreateDTO createDTO) {
        EmotionDiary diary = new EmotionDiary();
        diary.setUserId(userId);
        diary.setDiaryDate(createDTO.getDiaryDate());
        diary.setMoodScore(createDTO.getMoodScore());
        diary.setDominantEmotion(createDTO.getDominantEmotion());
        diary.setEmotionTriggers(createDTO.getEmotionTriggers());
        diary.setDiaryContent(createDTO.getDiaryContent());
        diary.setSleepQuality(createDTO.getSleepQuality());
        diary.setStressLevel(createDTO.getStressLevel());
        emotionDiaryMapper.insert(diary);
        return diary;
    }

    /**
     * 查询某用户的情绪日记列表（按日期倒序）
     */
    public List<EmotionDiary> listByUserId(Long userId) {
        LambdaQueryWrapper<EmotionDiary> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EmotionDiary::getUserId, userId)
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getId);
        return emotionDiaryMapper.selectList(wrapper);
    }

    /**
     * 分页查询全部学生的情绪日记（辅导员视角）。
     * <p>
     * 排序刻意为「心情分升序 + 日期倒序」：辅导员最需要先看到状态最差的学生，
     * 而不是最新提交的那条。这与学生端「看自己的记录」的排序诉求完全不同。
     * <p>
     * <b>权限说明</b>：返回全体学生的日记内容，调用方必须已校验辅导员身份。
     *
     * @param days 只统计最近多少天，null 表示不限
     */
    public PageResult<EmotionDiary> pageAllForCounselor(Integer pageNum, Integer pageSize, Integer days) {
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null ? 10 : Math.min(pageSize, 50);
        Page<EmotionDiary> page = new Page<>(current, size);

        LambdaQueryWrapper<EmotionDiary> wrapper = new LambdaQueryWrapper<>();
        if (days != null && days > 0) {
            wrapper.ge(EmotionDiary::getDiaryDate, LocalDate.now().minusDays(days));
        }
        // 心情分低的排前面：状态最差的学生最需要被关注
        wrapper.orderByAsc(EmotionDiary::getMoodScore)
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getId);

        emotionDiaryMapper.selectPage(page, wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getSize(), page.getCurrent());
    }

    /**
     * 批量填充学生姓名。
     * <p>
     * 日记表只有 userId，辅导员看列表时需要知道「是谁」。
     * 一次性查出后在内存映射，避免逐条查询造成 N+1。
     */
    public Map<Long, String> loadUserNames(List<EmotionDiary> diaries) {
        Set<Long> userIds = diaries.stream()
                .map(EmotionDiary::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() != null && !u.getNickname().isBlank()
                                ? u.getNickname() : u.getUsername(),
                        (a, b) -> a));
    }
}
