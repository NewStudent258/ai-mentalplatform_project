package org.example.aispingboot.DTO.command;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单闭环请求参数。
 * <p>
 * handleResult 的必填校验放在服务层而非此处：注解校验失败返回的是参数错误，
 * 而这里更希望给出「请填写处置结果后再闭环」这样带业务语境的提示。
 */
@Data
public class WorkOrderCloseDTO {

    /** 处置结果，必填 */
    @Size(max = 1000, message = "处置结果最多1000个字符")
    private String handleResult;
}
