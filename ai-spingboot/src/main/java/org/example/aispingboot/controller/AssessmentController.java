package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.entity.AssessmentQuestion;
import org.example.aispingboot.entity.AssessmentRecord;
import org.example.aispingboot.entity.AssessmentScale;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.AssessmentService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 心理测评接口（学生端）。
 * <p>
 * 题目由服务端原样返回，模型不参与——这保证了量表措辞不被改写。
 */
@RestController
@RequestMapping("/api/assessment")
public class AssessmentController {

    @Autowired
    private AssessmentService assessmentService;

    /**
     * 获取待作答的测评（若有）。
     * <p>
     * 前端在收到 AI 回复后调用：Agent 通过工具推送了测评，界面据此弹出答题窗。
     * 返回 null 表示当前没有待作答的测评。
     */
    @GetMapping("/pending")
    public Result<Map<String, Object>> pending() {
        Long userId = getCurrentUserId();
        AssessmentRecord record = assessmentService.getPending(userId);
        if (record == null) {
            return Result.ok(null);
        }
        return Result.ok(buildPaper(record));
    }

    /** 主动发起测评（用户自己在界面上选择量表） */
    @PostMapping("/start")
    public Result<Map<String, Object>> start(@RequestBody Map<String, String> body) {
        Long userId = getCurrentUserId();
        String scaleCode = body == null ? null : body.get("scaleCode");
        if (scaleCode == null) {
            throw new BusinessException("请指定量表类型");
        }
        Long recordId = assessmentService.startAssessment(userId, null, scaleCode);
        // 直接用刚创建的记录构建试卷：若改查「待作答」，可能拿到更早的遗留记录
        return Result.ok(buildPaper(assessmentService.getRecord(recordId)));
    }

    /**
     * 提交作答。
     * <p>
     * 计分在服务端完成；若命中风险题项，服务端会直接触发危机流程。
     */
    @PostMapping("/{recordId}/submit")
    public Result<AssessmentRecord> submit(@PathVariable Long recordId,
                                           @RequestBody Map<String, List<Integer>> body) {
        Long userId = getCurrentUserId();
        List<Integer> answers = body == null ? null : body.get("answers");
        return Result.ok(assessmentService.submit(userId, recordId, answers));
    }

    /** 本人的测评历史 */
    @GetMapping("/my")
    public Result<List<AssessmentRecord>> myRecords() {
        return Result.ok(assessmentService.listByUser(getCurrentUserId()));
    }

    /** 组装答题所需的数据：量表信息 + 题目 + 选项 */
    private Map<String, Object> buildPaper(AssessmentRecord record) {
        if (record == null) {
            return null;
        }
        AssessmentScale scale = assessmentService.getScale(record.getScaleCode());
        List<AssessmentQuestion> questions = assessmentService.getQuestions(record.getScaleCode());

        Map<String, Object> paper = new LinkedHashMap<>();
        paper.put("recordId", record.getId());
        paper.put("scaleCode", scale.getCode());
        paper.put("scaleName", scale.getName());
        paper.put("description", scale.getDescription());
        // 选项统一为标准的 0-3 分制，由服务端下发而非前端硬编码
        paper.put("options", AssessmentService.OPTION_LABELS);
        paper.put("questions", questions.stream().map(q -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("orderNo", q.getOrderNo());
            item.put("content", q.getContent());
            return item;
        }).toList());
        return paper;
    }

    private Long getCurrentUserId() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }
}
