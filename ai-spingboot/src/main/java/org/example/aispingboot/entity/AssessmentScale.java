package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 心理量表定义。
 */
@Data
@TableName("assessment_scale")
public class AssessmentScale {

    /** 情绪状态自评（抑郁相关） */
    public static final String CODE_PHQ9 = "PHQ9";
    /** 焦虑状态自评 */
    public static final String CODE_GAD7 = "GAD7";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;

    private String name;

    private String description;

    private Integer enabled;

    private LocalDateTime createdAt;
}
