package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.entity.AssessmentQuestion;
import org.example.aispingboot.entity.AssessmentRecord;
import org.example.aispingboot.entity.AssessmentScale;
import org.example.aispingboot.entity.CrisisEvent;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.AssessmentQuestionMapper;
import org.example.aispingboot.mapper.AssessmentRecordMapper;
import org.example.aispingboot.mapper.AssessmentScaleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 心理测评服务。
 * <p>
 * <b>本服务的核心职责是「受控」</b>：
 * <ul>
 *   <li>题目来自预置题库，模型不能生成或改写；</li>
 *   <li>计分由服务端按标准规则计算，模型不能参与；</li>
 *   <li>风险判定（如 PHQ-9 第 9 题得分 &gt; 0）由服务端识别并触发危机流程。</li>
 * </ul>
 * 模型在这条链路上只做一件事——<b>判断「什么时候该建议用户做测评」</b>。
 * 这是「不确定的能力交给模型，确定的规则交回代码」这一原则的又一次落地。
 */
@Service
public class AssessmentService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentService.class);

    /** 题项选项：标准的 0-3 分制，PHQ-9 与 GAD-7 通用 */
    public static final List<String> OPTION_LABELS = List.of(
            "完全不会", "好几天", "超过一半的时间", "几乎每天");

    /** 单次作答的题数上限，防止越界数据 */
    private static final int MAX_QUESTIONS = 30;

    private final AssessmentScaleMapper assessmentScaleMapper;
    private final AssessmentQuestionMapper assessmentQuestionMapper;
    private final AssessmentRecordMapper assessmentRecordMapper;
    private final CrisisService crisisService;

    public AssessmentService(AssessmentScaleMapper assessmentScaleMapper,
                             AssessmentQuestionMapper assessmentQuestionMapper,
                             AssessmentRecordMapper assessmentRecordMapper,
                             CrisisService crisisService) {
        this.assessmentScaleMapper = assessmentScaleMapper;
        this.assessmentQuestionMapper = assessmentQuestionMapper;
        this.assessmentRecordMapper = assessmentRecordMapper;
        this.crisisService = crisisService;
    }

    /** 取量表定义 */
    public AssessmentScale getScale(String scaleCode) {
        AssessmentScale scale = assessmentScaleMapper.selectOne(
                new LambdaQueryWrapper<AssessmentScale>().eq(AssessmentScale::getCode, scaleCode));
        if (scale == null || !Integer.valueOf(1).equals(scale.getEnabled())) {
            throw new BusinessException("量表不存在或已停用");
        }
        return scale;
    }

    /** 取量表题目（按题号升序） */
    public List<AssessmentQuestion> getQuestions(String scaleCode) {
        return assessmentQuestionMapper.selectList(
                new LambdaQueryWrapper<AssessmentQuestion>()
                        .eq(AssessmentQuestion::getScaleCode, scaleCode)
                        .orderByAsc(AssessmentQuestion::getOrderNo));
    }

    /**
     * 触发一次测评：创建待作答记录。
     * <p>
     * 由 Agent 通过工具调用，或用户在前端主动发起。
     * <p>
     * <b>为什么先建「待作答」记录而不是直接返回题目</b>：
     * 这样即使用户中途关掉页面没有作答，也能留下「曾被建议做测评」的痕迹——
     * 对辅导员而言，「建议了但没做」本身就是值得关注的信息。
     *
     * @return 新建的测评记录ID
     */
    public Long startAssessment(Long userId, Long sessionId, String scaleCode) {
        // 校验量表合法，避免 Agent 传入手写的量表编码
        getScale(scaleCode);

        AssessmentRecord record = AssessmentRecord.builder()
                .userId(userId)
                .sessionId(sessionId)
                .scaleCode(scaleCode)
                .status(AssessmentRecord.STATUS_PENDING)
                .riskFlag(0)
                .createdAt(LocalDateTime.now())
                .build();
        assessmentRecordMapper.insert(record);

        log.info("用户 {} 触发测评 {}，记录 {}", userId, scaleCode, record.getId());
        return record.getId();
    }

    /**
     * 提交作答并计分。
     * <p>
     * 计分完全在服务端完成：量表是有标准算法的工具，交给模型既无必要也不可靠。
     * 若风险题项命中，会同时触发危机流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public AssessmentRecord submit(Long userId, Long recordId, List<Integer> answers) {
        AssessmentRecord record = assessmentRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("测评记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException("无权提交他人的测评");
        }
        if (AssessmentRecord.STATUS_COMPLETED == record.getStatus()) {
            throw new BusinessException("该测评已完成，请勿重复提交");
        }

        List<AssessmentQuestion> questions = getQuestions(record.getScaleCode());
        if (answers == null || answers.size() != questions.size()) {
            throw new BusinessException("作答数量与题目数量不一致");
        }
        if (answers.size() > MAX_QUESTIONS) {
            throw new BusinessException("作答数量异常");
        }

        int total = 0;
        boolean riskHit = false;
        for (int i = 0; i < answers.size(); i++) {
            Integer score = answers.get(i);
            if (score == null || score < 0 || score > 3) {
                throw new BusinessException("第 " + (i + 1) + " 题的作答超出取值范围");
            }
            total += score;
            // 风险题项只要得分大于 0 就标记：总分可能落在「轻度」区间，
            // 但自伤念头的出现本身就需要立即介入，不能等总分升高
            if (Integer.valueOf(1).equals(questions.get(i).getRiskItem()) && score > 0) {
                riskHit = true;
            }
        }

        record.setAnswers(answers.stream().map(String::valueOf).collect(Collectors.joining(",")));
        record.setTotalScore(total);
        record.setSeverity(resolveSeverity(record.getScaleCode(), total));
        record.setRiskFlag(riskHit ? 1 : 0);
        record.setStatus(AssessmentRecord.STATUS_COMPLETED);
        record.setCompletedAt(LocalDateTime.now());
        assessmentRecordMapper.updateById(record);

        log.info("用户 {} 完成测评 {}，总分 {}（{}），风险标记 {}",
                userId, record.getScaleCode(), total, record.getSeverity(), riskHit);

        // 风险题项命中 → 直接进入危机干预闭环，不依赖情绪分析的异步识别
        if (riskHit) {
            triggerCrisis(userId, record);
        }
        return record;
    }

    /**
     * 风险题项命中时触发危机建单。
     * <p>
     * 与情绪分析走的是同一个 {@link CrisisService}，因此共享去重逻辑——
     * 若同一会话已有未闭环工单，不会重复建单。
     */
    private void triggerCrisis(Long userId, AssessmentRecord record) {
        try {
            String reason = String.format("量表 %s 的风险题项得分大于 0（总分 %d，%s）",
                    record.getScaleCode(), record.getTotalScore(), record.getSeverity());

            // 复用危机事件表记录这次测评触发的风险，便于事后回溯
            CrisisEvent event = CrisisEvent.builder()
                    .userId(userId)
                    .sessionId(record.getSessionId() == null ? 0L : record.getSessionId())
                    .riskLevel(3)
                    .primaryEmotion("测评风险项")
                    .emotionScore(null)
                    .triggerMessage(reason + "：" + record.getAnswers())
                    .createdAt(LocalDateTime.now())
                    .build();

            Long orderId = crisisService.reportCrisisByEvent(userId, event);
            if (orderId != null) {
                log.warn("测评 {} 命中风险题项，已创建危机工单 {}", record.getId(), orderId);
            }
        } catch (Exception e) {
            // 建单失败不能影响测评提交本身，但必须留下 error 日志供人工核查
            log.error("测评 {} 命中风险题项但建单失败，需人工核查：{}", record.getId(), e.getMessage(), e);
        }
    }

    /** 查询用户的测评历史 */
    public List<AssessmentRecord> listByUser(Long userId) {
        return assessmentRecordMapper.selectList(
                new LambdaQueryWrapper<AssessmentRecord>()
                        .eq(AssessmentRecord::getUserId, userId)
                        .orderByDesc(AssessmentRecord::getCreatedAt));
    }

    /** 按ID查询测评记录 */
    public AssessmentRecord getRecord(Long recordId) {
        return recordId == null ? null : assessmentRecordMapper.selectById(recordId);
    }

    /** 查询待作答的测评（前端据此弹出答题界面） */
    public AssessmentRecord getPending(Long userId) {
        return assessmentRecordMapper.selectOne(
                new LambdaQueryWrapper<AssessmentRecord>()
                        .eq(AssessmentRecord::getUserId, userId)
                        .eq(AssessmentRecord::getStatus, AssessmentRecord.STATUS_PENDING)
                        .orderByDesc(AssessmentRecord::getCreatedAt)
                        .last("limit 1"));
    }

    /**
     * 按标准分界给出严重程度。
     * <p>
     * 分界值来自量表的标准划界，写在代码而非配置里：
     * 它们是临床共识、不应被随意调整；写成配置反而容易被误改而使评估失去可比性。
     */
    private String resolveSeverity(String scaleCode, int total) {
        if (AssessmentScale.CODE_PHQ9.equals(scaleCode)) {
            if (total <= 4) return "无明显";
            if (total <= 9) return "轻度";
            if (total <= 14) return "中度";
            if (total <= 19) return "中重度";
            return "重度";
        }
        if (AssessmentScale.CODE_GAD7.equals(scaleCode)) {
            if (total <= 4) return "无明显";
            if (total <= 9) return "轻度";
            if (total <= 14) return "中度";
            return "重度";
        }
        return StringUtils.hasText(scaleCode) ? "已评估" : "未知";
    }
}
