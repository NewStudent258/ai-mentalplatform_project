package org.example.aispingboot.enumClass;

import lombok.Getter;

/**
 * 知识文章状态。
 * <p>
 * 在原有「草稿/已发布/已下线」基础上，为「用户投稿 + 管理员审核」补充了
 * 待审核与已驳回两个状态。用户投稿先进 {@link #PENDING_REVIEW}，
 * 管理员审核后才可能进入 {@link #PUBLISHED}。
 */
@Getter
public enum ArticleStatus {

    /** 管理员创建的未发布内容 */
    DRAFT(0, "草稿"),
    /** 已发布：对用户可见 */
    PUBLISHED(1, "已发布"),
    /** 已下线：发布后又被撤下 */
    UNPUBLISHED(2, "已下线"),
    /** 用户投稿后等待管理员审核 */
    PENDING_REVIEW(3, "待审核"),
    /** 审核未通过 */
    REJECTED(4, "已驳回");

    private final Integer code;
    private final String description;

    ArticleStatus(Integer code, String description) {
        this.code = code;
        this.description = description;
    }

    public static ArticleStatus fromCode(Integer code) {
        for (ArticleStatus status : ArticleStatus.values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的文章状态代码: " + code);
    }

    /** 是否为「对普通用户可见」的状态 */
    public static boolean isVisibleToUser(Integer code) {
        return PUBLISHED.getCode().equals(code);
    }
}
