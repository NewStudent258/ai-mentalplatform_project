package org.example.aispingboot.common;

import lombok.Data;

import java.util.List;

/**
 * 统一分页返回结构（可安全序列化进 Redis 缓存）
 * 字段与 MyBatis-Plus Page 的 JSON 输出保持一致（records/total/size/current）
 */
@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long size;
    private long current;

    public PageResult() {
    }

    public PageResult(List<T> records, long total, long size, long current) {
        this.records = records;
        this.total = total;
        this.size = size;
        this.current = current;
    }
}
