package org.example.aispingboot.enumClass;

import lombok.Getter;

/**
 * 危机工单状态。
 * <p>
 * 状态流转是单向的：待认领 → 处理中 → 已闭环。
 * 服务层会拒绝所有倒退或跳跃的流转（例如「已闭环」不能再被认领），
 * 避免并发操作或误操作破坏处置记录的可信度。
 */
@Getter
public enum WorkOrderStatus {

    /** 已建单，等待辅导员认领 */
    PENDING(0, "待认领"),

    /** 已有辅导员认领，正在处置 */
    PROCESSING(1, "处理中"),

    /** 处置完成，已填写处置结果 */
    CLOSED(2, "已闭环");

    private final Integer code;
    private final String description;

    WorkOrderStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static WorkOrderStatus fromCode(Integer code) {
        for (WorkOrderStatus status : WorkOrderStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的工单状态代码: " + code);
    }

    /** 是否为未闭环状态（待认领或处理中） */
    public static boolean isOpen(Integer code) {
        return PENDING.getCode().equals(code) || PROCESSING.getCode().equals(code);
    }
}
