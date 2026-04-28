package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心理测评记录。
 * <p>
 * <b>总分由服务端计算，不由模型计算</b>：量表计分是确定的规则运算，
 * 交给模型既无必要也不可靠（可能算错，且结果不可复现）。
 * 模型可以参与「解读结果、给出建议」，但不参与计分。
 */
@Data
@TableName("assessment_record")
@Builder
public class AssessmentRecord {

    /** 待作答 */
    public static final int STATUS_PENDING = 0;
    /** 已完成 */
    public static final int STATUS_COMPLETED = 1;
    /** 已放弃 */
    public static final int STATUS_ABANDONED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    /** 触发测评的会话（Agent 主动触发时有值） */
    @TableField("session_id")
    private Long sessionId;

    @TableField("scale_code")
    private String scaleCode;

    private Integer status;

    /** 作答明细，逗号分隔，如 {@code 1,2,0,3} */
    private String answers;

    /** 总分，服务端计算 */
    @TableField("total_score")
    private Integer totalScore;

    /** 严重程度描述 */
    private String severity;

    /** 是否命中风险题项（得分>0），命中时需走危机流程 */
    @TableField("risk_flag")
    private Integer riskFlag;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("completed_at")
    private LocalDateTime completedAt;
}
