package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.example.aispingboot.DTO.command.EmotionDiaryCreateDTO;
import org.example.aispingboot.common.PageResult;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.EmotionDiary;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.EmotionDiaryService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {
    @Resource
    private EmotionDiaryService emotionDiaryService;

    // 提交情绪日记
    @PostMapping
    public Result<EmotionDiary> createDiary(@Valid @RequestBody EmotionDiaryCreateDTO createDTO) {
        // 从token中解析出当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        EmotionDiary diary = emotionDiaryService.createDiary(userId, createDTO);
        return Result.ok(diary);
    }

    // 获取当前用户的情绪日记列表
    @GetMapping("/my")
    public Result<List<EmotionDiary>> getMyDiaries() {
        // 从token中解析出当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        return Result.ok(emotionDiaryService.listByUserId(userId));
    }

    /**
     * 全部学生的情绪日记（仅辅导员）。
     * <p>
     * 默认按心情分升序排列——辅导员打开这个页面，最该先看到的是状态最差的学生，
     * 而不是最新提交的那条记录。
     *
     * @param days 只看最近多少天的记录，不传表示不限
     */
    @GetMapping("/counselor/page")
    public Result<PageResult<Map<String, Object>>> counselorDiaryPage(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) Integer days) {
        checkCounselor();

        PageResult<EmotionDiary> page = emotionDiaryService.pageAllForCounselor(pageNum, pageSize, days);
        // 补充学生姓名，否则列表上只有一串「用户ID」，辅导员无从判断是谁
        Map<Long, String> userNameMap = emotionDiaryService.loadUserNames(page.getRecords());

        List<Map<String, Object>> records = page.getRecords().stream().map(diary -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", diary.getId());
            item.put("userId", diary.getUserId());
            item.put("userName", userNameMap.get(diary.getUserId()));
            item.put("diaryDate", diary.getDiaryDate());
            item.put("moodScore", diary.getMoodScore());
            item.put("dominantEmotion", diary.getDominantEmotion());
            item.put("emotionTriggers", diary.getEmotionTriggers());
            item.put("sleepQuality", diary.getSleepQuality());
            item.put("stressLevel", diary.getStressLevel());
            // 日记正文属于学生最私密的表达。辅导员承担干预职责、需要据此判断状态，
            // 因此保留该字段；若后续要收紧，这里改为按「是否有未闭环工单」按需返回即可。
            item.put("diaryContent", diary.getDiaryContent());
            item.put("createdAt", diary.getCreatedAt());
            return item;
        }).collect(Collectors.toList());

        return Result.ok(new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent()));
    }

    /** 校验辅导员身份（roleType=3） */
    private void checkCounselor() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Integer roleType = jwt.getClaim("roleType").asInt();
        if (roleType == null || roleType != 3) {
            throw new BusinessException("无权限操作，仅辅导员可查看学生情绪日志");
        }
    }
}
