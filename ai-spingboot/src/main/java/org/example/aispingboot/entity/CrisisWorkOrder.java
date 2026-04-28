package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关怀工单（处理流程，状态可变）。
 * <p>
 * 承载「认领 → 处置 → 闭环」的工作流：状态由 {@link org.example.aispingboot.enumClass.WorkOrderStatus}
 * 约束，非法流转在服务层拦截。
 * <p>
 * <b>关于表名</b>：表名保留了 {@code crisis_work_order}，但它的职责已经扩展——
 * 除了「AI 识别高危自动建单」，还承载「Agent 判断需要人工介入时主动转介」。
 * 两者都是「需要辅导员跟进的事项」，因此共用一条队列，用 {@code source} 区分来源。
 * <p>
 * 刻意不为转介单独建表：辅导员需要的是<b>一个收件箱</b>而非两个。
 * 紧急程度用 {@code urgency} 表达，列表按它排序即可保证高危优先。
 */
@Data
@TableName("crisis_work_order")
@Builder
public class CrisisWorkOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的危机事件ID。
     * <p>
     * 可为 null：Agent 主动转介（如学生说「我想找人聊聊」）并非由风险识别触发，
     * 不存在对应的危机事件。
     */
    @TableField("event_id")
    private Long eventId;

    /** 工单来源，见 {@link org.example.aispingboot.enumClass.WorkOrderSource} */
    private String source;

    /** 紧急程度 1:高 2:中 3:低 */
    private Integer urgency;

    /** 转介原因（非 AI 识别场景下由 Agent 或学生给出） */
    @TableField("escalate_reason")
    private String escalateReason;

    /**
     * 是否已因超时被升级。
     * <p>
     * 需要这个标记是因为「升级」可能被触发多次：延迟消息可能重复投递，
     * 管理员也可能手工重放。没有标记会导致同一条工单反复通知管理员。
     */
    private Integer escalated;

    @TableField("escalated_at")
    private LocalDateTime escalatedAt;

    /** 学生用户ID（冗余字段，便于直接按学生查询而无需联表） */
    @TableField("user_id")
    private Long userId;

    /** 状态，见 WorkOrderStatus */
    private Integer status;

    /** 处理人（辅导员）用户ID */
    @TableField("handler_id")
    private Long handlerId;

    /** 处理人姓名（冗余，列表展示时免联表） */
    @TableField("handler_name")
    private String handlerName;

    /** 认领时间 */
    @TableField("claimed_at")
    private LocalDateTime claimedAt;

    /** 闭环时间 */
    @TableField("closed_at")
    private LocalDateTime closedAt;

    /**
     * 处置结果。
     * <p>
     * 闭环时必填：留痕的意义在于「做了什么」，只标记「已处理」而不写处置内容，
     * 事后无法追溯，等于没有闭环。
     */
    @TableField("handle_result")
    private String handleResult;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
