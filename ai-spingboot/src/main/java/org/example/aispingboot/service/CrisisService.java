package org.example.aispingboot.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.aispingboot.DTO.response.CrisisWorkOrderDTO;
import org.example.aispingboot.entity.CrisisEvent;
import org.example.aispingboot.entity.CrisisWorkOrder;
import org.example.aispingboot.entity.Notification;
import org.example.aispingboot.enumClass.WorkOrderSource;
import org.example.aispingboot.enumClass.WorkOrderStatus;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.CrisisEventMapper;
import org.example.aispingboot.mapper.CrisisWorkOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 危机干预闭环。
 * <p>
 * <b>它解决什么问题</b>：改造前，AI 识别到高危用户后只打一条 ERROR 日志——
 * 识别是准确的，但「识别完就没有下文」：没有人被通知、没有记录、没有人跟进。
 * 本服务把「识别」与「处理」连成闭环：
 * <pre>
 *   风险等级 ≥ 2
 *        ↓
 *   ① 写入危机事件（留痕，不可变）
 *   ② 自动创建工单（待认领）
 *        ↓
 *   ③ 辅导员认领（处理中）
 *   ④ 填写处置结果（已闭环）
 *        ↓
 *   全程可回溯
 * </pre>
 */
@Service
public class CrisisService {

    private static final Logger log = LoggerFactory.getLogger(CrisisService.class);

    /** 触发建单的最低风险等级：2 预警及以上 */
    public static final int CRISIS_RISK_THRESHOLD = 2;

    private final CrisisEventMapper crisisEventMapper;
    private final CrisisWorkOrderMapper crisisWorkOrderMapper;
    private final NotificationService notificationService;
    private final CrisisTimeoutProducer crisisTimeoutProducer;

    public CrisisService(CrisisEventMapper crisisEventMapper,
                         CrisisWorkOrderMapper crisisWorkOrderMapper,
                         NotificationService notificationService,
                         CrisisTimeoutProducer crisisTimeoutProducer) {
        this.crisisEventMapper = crisisEventMapper;
        this.crisisWorkOrderMapper = crisisWorkOrderMapper;
        this.notificationService = notificationService;
        this.crisisTimeoutProducer = crisisTimeoutProducer;
    }

    /**
     * 记录危机事件并自动建单。
     * <p>
     * 由情绪分析在识别到高风险时调用。整个过程对主对话链路是「旁路」的：
     * 任何异常都只记录日志，绝不影响用户当前对话。
     *
     * @return 新建的工单ID；若因去重跳过则返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reportCrisis(Long userId, Long sessionId, Integer riskLevel,
                             String primaryEmotion, Integer emotionScore, String triggerMessage) {
        if (riskLevel == null || riskLevel < CRISIS_RISK_THRESHOLD) {
            return null;
        }
        if (userId == null || sessionId == null) {
            log.warn("危机上报参数不完整，已跳过：userId={} sessionId={}", userId, sessionId);
            return null;
        }

        // 注意：会话级去重统一由 createOrderWithEvent 处理，此处不再重复实现，
        // 避免两条风险路径的去重规则各写一遍而逐渐产生分歧。

        // 写入事件并建单
        CrisisEvent event = CrisisEvent.builder()
                .userId(userId)
                .sessionId(sessionId)
                .riskLevel(riskLevel)
                .primaryEmotion(primaryEmotion)
                .emotionScore(emotionScore)
                .triggerMessage(triggerMessage)
                .createdAt(LocalDateTime.now())
                .build();

        Long orderId = createOrderWithEvent(event, riskLevel >= 3 ? URGENCY_HIGH : URGENCY_MEDIUM);
        if (orderId != null) {
            log.warn("【危机建单】会话 {} 风险等级 {}，已创建工单 {}，等待辅导员认领",
                    sessionId, riskLevel, orderId);
        }
        return orderId;
    }

    /**
     * 基于外部构造的危机事件建单。
     * <p>
     * 供测评等其他风险来源使用：量表的风险题项命中时，同样需要进入危机干预闭环，
     * 但触发路径与情绪分析不同，因此由调用方负责构造事件。
     *
     * @return 新建工单ID；因去重跳过时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long reportCrisisByEvent(Long userId, CrisisEvent event) {
        if (userId == null || event == null) {
            return null;
        }
        // 测评触发的风险一律按最高优先级处理：它比对话中的情绪信号更明确
        return createOrderWithEvent(event, URGENCY_HIGH);
    }

    /**
     * 写入事件并创建工单（含会话级去重）。
     * <p>
     * 抽取为公共方法，使「情绪分析触发」与「测评触发」两条路径共用同一套
     * 去重与建单逻辑——否则两处实现迟早会不一致。
     */
    private Long createOrderWithEvent(CrisisEvent event, int urgency) {
        // 去重：同一会话若已有未闭环工单，不再重复建单。
        // 会话ID为 0 表示无关联会话（如测评直接触发），此时跳过会话级去重。
        Long sessionId = event.getSessionId();
        if (sessionId != null && sessionId > 0) {
            Long existing = findOpenOrderIdBySession(sessionId);
            if (existing != null) {
                log.info("会话 {} 已有未闭环工单 {}，本次不再重复建单", sessionId, existing);
                return null;
            }
        }

        crisisEventMapper.insert(event);

        CrisisWorkOrder order = CrisisWorkOrder.builder()
                .eventId(event.getId())
                .userId(event.getUserId())
                .source(WorkOrderSource.AUTO_DETECTED.name())
                .urgency(urgency)
                .escalated(0)
                .status(WorkOrderStatus.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .build();
        crisisWorkOrderMapper.insert(order);

        // 建单后立即通知辅导员并安排超时检查。
        // 两者都在事务提交前调用，但各自的失败都被内部吞掉——
        // 「通知没发出去」不该导致「工单被回滚」。
        notifyCounselors(order, event);
        crisisTimeoutProducer.scheduleTimeoutCheck(order.getId(), urgency);

        return order.getId();
    }

    /**
     * 通知辅导员有新工单。
     * <p>
     * 通知内容包含风险等级与触发上下文，让辅导员不必打开系统也能判断紧急程度。
     */
    private void notifyCounselors(CrisisWorkOrder order, CrisisEvent event) {
        String title;
        if (order.getEventId() != null) {
            title = String.format("【危机工单】检测到风险等级 %d，请尽快认领", event.getRiskLevel());
        } else {
            title = "【转介工单】有学生需要人工跟进";
        }

        StringBuilder content = new StringBuilder();
        content.append("学生 ID：").append(order.getUserId()).append("\n");
        if (event.getPrimaryEmotion() != null) {
            content.append("触发情绪：").append(event.getPrimaryEmotion()).append("\n");
        }
        if (event.getTriggerMessage() != null) {
            content.append("触发内容：").append(event.getTriggerMessage()).append("\n");
        }
        if (order.getEscalateReason() != null) {
            content.append("转介原因：").append(order.getEscalateReason()).append("\n");
        }
        content.append("\n请登录平台在「危机工单」中认领并跟进。");

        notificationService.notifyRole(3, Notification.TYPE_CRISIS_ORDER,
                title, content.toString(), order.getId());
    }

    /**
     * Agent 主动转介人工。
     * <p>
     * <b>与 {@link #reportCrisis} 的区别</b>：后者由风险等级这条系统规则触发，
     * 本方法由 Agent 对具体处境的理解触发——例如学生强烈表达了想找人倾诉的意愿，
     * 但情绪评估并未达到预警线。这是 Agent 自主性的一种体现：
     * <b>不是等规则命中，而是理解处境后主动行动</b>。
     * <p>
     * <b>为什么要去重</b>：Agent 可能在连续几轮里都判断需要转介，
     * 若每次都建单，辅导员的收件箱会被同一个学生刷屏，反而淹没真正紧急的事项。
     *
     * @param urgency 1 高 / 2 中 / 3 低
     * @return 新建工单ID；因去重跳过时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long escalateToHuman(Long userId, Long sessionId, String reason, Integer urgency) {
        if (userId == null || sessionId == null) {
            log.warn("Agent 转介参数不完整，已跳过：userId={} sessionId={}", userId, sessionId);
            return null;
        }

        // 与自动建单共用同一套去重：同一会话存在未闭环工单时不再重复建单
        Long existing = findOpenOrderIdBySession(sessionId);
        if (existing != null) {
            log.info("会话 {} 已有未闭环工单 {}，Agent 转介不再重复建单", sessionId, existing);
            return null;
        }

        CrisisWorkOrder order = CrisisWorkOrder.builder()
                // 主动转介没有对应的危机事件，eventId 留空
                .eventId(null)
                .userId(userId)
                .source(WorkOrderSource.AGENT_ESCALATED.name())
                .urgency(normalizeUrgency(urgency))
                .escalateReason(reason)
                .escalated(0)
                .status(WorkOrderStatus.PENDING.getCode())
                .createdAt(LocalDateTime.now())
                .build();
        crisisWorkOrderMapper.insert(order);

        // 与自动建单走同一套通知与超时调度。
        // 这里构造一个「空事件」仅用于拼装通知文案，不落库。
        notifyCounselors(order, CrisisEvent.builder().userId(userId).build());
        crisisTimeoutProducer.scheduleTimeoutCheck(order.getId(), order.getUrgency());

        log.info("【Agent 转介】会话 {} 已创建工单 {}，原因：{}", sessionId, order.getId(), reason);
        return order.getId();
    }

    /** 紧急性取值区间 */
    private static final int URGENCY_HIGH = 1;
    private static final int URGENCY_MEDIUM = 2;

    /** 钳制紧急程度到合法区间，避免 Agent 给出越界值导致排序错乱 */
    private Integer normalizeUrgency(Integer urgency) {
        if (urgency == null || urgency < URGENCY_HIGH || urgency > 3) {
            return URGENCY_MEDIUM;
        }
        return urgency;
    }

    /** 查找该会话下尚未闭环的工单 */
    private Long findOpenOrderIdBySession(Long sessionId) {
        // 工单表本身不存 sessionId，需先经事件表找到该会话全部事件，再查未闭环工单。
        // 这两个表都是按学生个体数据量级设计的，查询代价可接受。
        List<CrisisEvent> events = crisisEventMapper.selectList(
                new LambdaQueryWrapper<CrisisEvent>().eq(CrisisEvent::getSessionId, sessionId));
        if (events.isEmpty()) {
            return null;
        }
        List<Long> eventIds = events.stream().map(CrisisEvent::getId).toList();

        CrisisWorkOrder open = crisisWorkOrderMapper.selectOne(
                new LambdaQueryWrapper<CrisisWorkOrder>()
                        .in(CrisisWorkOrder::getEventId, eventIds)
                        .ne(CrisisWorkOrder::getStatus, WorkOrderStatus.CLOSED.getCode())
                        .last("limit 1"));
        return open == null ? null : open.getId();
    }

    /**
     * 工单列表。
     *
     * @param status 状态筛选，null 表示全部
     */
    public List<CrisisWorkOrderDTO> listWorkOrders(Integer status) {
        LambdaQueryWrapper<CrisisWorkOrder> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(CrisisWorkOrder::getStatus, status);
        }
        // 排序即优先级：先看未闭环的，再看紧急程度高的（危机 > 预警 > 主动转介），
        // 最后按建单时间倒序。辅导员打开页面时，最该处理的自然浮到最前。
        wrapper.orderByAsc(CrisisWorkOrder::getStatus)
                .orderByAsc(CrisisWorkOrder::getUrgency)
                .orderByDesc(CrisisWorkOrder::getCreatedAt);
        List<CrisisWorkOrder> orders = crisisWorkOrderMapper.selectList(wrapper);

        List<CrisisWorkOrderDTO> result = new ArrayList<>(orders.size());
        for (CrisisWorkOrder order : orders) {
            result.add(toDTO(order));
        }
        return result;
    }

    /**
     * 认领工单：待认领 → 处理中。
     * <p>
     * 只有「待认领」状态可以被认领，已被他人认领的工单会拒绝，
     * 避免两个人同时处理同一个学生。
     */
    public void claim(Long orderId, Long handlerId, String handlerName) {
        CrisisWorkOrder order = getOrderOrThrow(orderId);

        if (!WorkOrderStatus.PENDING.getCode().equals(order.getStatus())) {
            throw new BusinessException("该工单已被认领或已闭环，无法重复认领");
        }

        order.setStatus(WorkOrderStatus.PROCESSING.getCode());
        order.setHandlerId(handlerId);
        order.setHandlerName(handlerName);
        order.setClaimedAt(LocalDateTime.now());
        crisisWorkOrderMapper.updateById(order);

        log.info("工单 {} 已被 {}（{}）认领", orderId, handlerName, handlerId);
    }

    /**
     * 处置闭环：处理中 → 已闭环。
     * <p>
     * 必须填写处置结果：留痕的意义在于「做了什么」，
     * 只点一下「已处理」而不写内容，事后无法追溯，等于没有闭环。
     */
    public void close(Long orderId, Long handlerId, String handleResult) {
        CrisisWorkOrder order = getOrderOrThrow(orderId);

        if (!StringUtils.hasText(handleResult)) {
            throw new BusinessException("请填写处置结果后再闭环");
        }
        if (WorkOrderStatus.CLOSED.getCode().equals(order.getStatus())) {
            throw new BusinessException("该工单已闭环，请勿重复操作");
        }
        if (order.getHandlerId() != null && !order.getHandlerId().equals(handlerId)) {
            throw new BusinessException("该工单由其他辅导员处理中，无法操作");
        }

        order.setStatus(WorkOrderStatus.CLOSED.getCode());
        // 若未经认领直接闭环（辅导员当场处理完），一并补上处理人
        if (order.getHandlerId() == null) {
            order.setHandlerId(handlerId);
            order.setClaimedAt(LocalDateTime.now());
        }
        order.setHandleResult(handleResult.trim());
        order.setClosedAt(LocalDateTime.now());
        crisisWorkOrderMapper.updateById(order);

        log.info("工单 {} 已闭环，处置人 {}", orderId, handlerId);
    }

    private CrisisWorkOrder getOrderOrThrow(Long orderId) {
        CrisisWorkOrder order = crisisWorkOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException("工单不存在");
        }
        return order;
    }

    /**
     * 组装工单 DTO（含关联的危机事件信息）。
     * <p>
     * 事件与工单一起返回：辅导员看工单时必然需要知道「为什么建的单」——
     * 触发时的情绪、风险等级与那句话，否则无法判断如何介入。
     */
    private CrisisWorkOrderDTO toDTO(CrisisWorkOrder order) {
        CrisisWorkOrderDTO dto = new CrisisWorkOrderDTO();
        dto.setId(order.getId());
        dto.setEventId(order.getEventId());
        dto.setUserId(order.getUserId());
        dto.setSource(order.getSource());
        dto.setSourceDesc(WorkOrderSource.fromCode(order.getSource()).getDescription());
        dto.setUrgency(order.getUrgency());
        dto.setEscalateReason(order.getEscalateReason());
        dto.setStatus(order.getStatus());
        dto.setStatusDesc(WorkOrderStatus.fromCode(order.getStatus()).getDescription());
        dto.setHandlerId(order.getHandlerId());
        dto.setHandlerName(order.getHandlerName());
        dto.setClaimedAt(order.getClaimedAt());
        dto.setClosedAt(order.getClosedAt());
        dto.setHandleResult(order.getHandleResult());
        dto.setCreatedAt(order.getCreatedAt());

        CrisisEvent event = crisisEventMapper.selectById(order.getEventId());
        if (event != null) {
            dto.setRiskLevel(event.getRiskLevel());
            dto.setPrimaryEmotion(event.getPrimaryEmotion());
            dto.setEmotionScore(event.getEmotionScore());
            dto.setTriggerMessage(event.getTriggerMessage());
            dto.setSessionId(event.getSessionId());
            dto.setEventCreatedAt(event.getCreatedAt());
        }
        return dto;
    }
}
