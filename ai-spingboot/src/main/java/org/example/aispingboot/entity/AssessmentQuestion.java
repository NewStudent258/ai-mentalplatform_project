package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心理量表题目（预置内容）。
 * <p>
 * <b>题目为什么必须预置、不能让模型生成</b>：
 * PHQ-9、GAD-7 是经过临床验证的标准化量表，题目措辞、顺序、计分方式都经过研究确定。
 * 一旦由模型"用自己的话转述"，量表就失去了信效度——测出来的分数没有参考意义，
 * 甚至可能因为措辞偏差而误判风险。
 * <p>
 * 因此本项目的分工是：<b>AI 只决定「何时该测」，题目与计分完全由服务端负责</b>。
 * 这与安全话术预置、练习库预置是同一套「受控工具」思路。
 */
@Data
@TableName("assessment_question")
public class AssessmentQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属量表编码 */
    @TableField("scale_code")
    private String scaleCode;

    /** 题号，从 1 开始 */
    @TableField("order_no")
    private Integer orderNo;

    /** 题目内容 */
    private String content;

    /**
     * 是否为风险题项。
     * <p>
     * 目前仅 PHQ-9 第 9 题（自伤念头）标记为风险题项：
     * 该题得分大于 0 即意味着存在自伤风险，需要立即走危机干预流程，
     * 而不能只看总分——总分可能落在"轻度"区间，但风险信号已经出现。
     */
    @TableField("risk_item")
    private Integer riskItem;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
