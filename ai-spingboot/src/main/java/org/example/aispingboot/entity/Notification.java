package org.example.aispingboot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内通知。
 * <p>
 * <b>为什么需要它</b>：危机工单创建后只是一条数据库记录，
 * 若辅导员不去看列表，高危学生就没人跟进——「建了单但没人知道」与「没建单」没有区别。
 * 通知把「有事发生」主动推给该处理的人，闭环才真正成立。
 */
@Data
@TableName("notification")
@Builder
public class Notification {

    /** 新危机工单（通知辅导员） */
    public static final String TYPE_CRISIS_ORDER = "CRISIS_ORDER";
    /** 工单超时未认领被升级（通知管理员） */
    public static final String TYPE_ORDER_ESCALATED = "ORDER_ESCALATED";
    /** 工单已被认领（通知建单人/相关方） */
    public static final String TYPE_ORDER_CLAIMED = "ORDER_CLAIMED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收人用户ID */
    @TableField("user_id")
    private Long userId;

    /** 通知类型，见本类常量 */
    private String type;

    private String title;

    private String content;

    /** 关联业务ID（工单ID等），便于前端跳转 */
    @TableField("biz_id")
    private Long bizId;

    /** 是否已读 0:未读 1:已读 */
    @TableField("is_read")
    private Integer isRead;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
