package org.example.aispingboot.controller;

import com.auth0.jwt.interfaces.DecodedJWT;
import org.example.aispingboot.DTO.command.WorkOrderCloseDTO;
import org.example.aispingboot.DTO.response.CrisisWorkOrderDTO;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.CrisisService;
import org.example.aispingboot.service.UserService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 危机工单接口（仅辅导员 / 咨询师）。
 * <p>
 * 权限刻意限定为辅导员角色（roleType=3），管理员无权访问：
 * 工单包含学生的高危对话原文等极度敏感的信息，
 * 按最小必要知悉原则，只应由承担干预职责的辅导员查看。
 * 这与「系统管理员不接触学生个体数据」的边界保持一致。
 */
@RestController
@RequestMapping("/api/crisis")
public class CrisisController {

    /** 辅导员 / 咨询师角色标识 */
    private static final int ROLE_COUNSELOR = 3;

    @Autowired
    private CrisisService crisisService;

    @Autowired
    private UserService userService;

    /**
     * 工单列表。
     *
     * @param status 状态筛选（0待认领 1处理中 2已闭环），不传则返回全部
     */
    @GetMapping("/work-orders")
    public Result<List<CrisisWorkOrderDTO>> list(@RequestParam(required = false) Integer status) {
        checkCounselor();
        return Result.ok(crisisService.listWorkOrders(status));
    }

    /** 认领工单 */
    @PostMapping("/work-orders/{id}/claim")
    public Result<Void> claim(@PathVariable Long id) {
        DecodedJWT jwt = checkCounselor();
        Long handlerId = jwt.getClaim("userId").asLong();
        String handlerName = userService.getUserById(handlerId).getDisplayName();
        crisisService.claim(id, handlerId, handlerName);
        return Result.ok(null);
    }

    /** 处置闭环（必须填写处置结果） */
    @PostMapping("/work-orders/{id}/close")
    public Result<Void> close(@PathVariable Long id, @RequestBody WorkOrderCloseDTO closeDTO) {
        DecodedJWT jwt = checkCounselor();
        Long handlerId = jwt.getClaim("userId").asLong();
        crisisService.close(id, handlerId, closeDTO == null ? null : closeDTO.getHandleResult());
        return Result.ok(null);
    }

    /** 校验辅导员身份并返回解码后的 JWT */
    private DecodedJWT checkCounselor() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Integer roleType = jwt.getClaim("roleType").asInt();
        if (roleType == null || roleType != ROLE_COUNSELOR) {
            throw new BusinessException("无权限操作，仅辅导员可处理危机工单");
        }
        return jwt;
    }
}
