package org.example.aispingboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.DTO.response.ConsultationSessionListItemDTO;
import org.example.aispingboot.common.PageResult;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.entity.User;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.mapper.ConsultationSessionMapper;
import org.example.aispingboot.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    public ConsultationSession createSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 验证用户是否存在
        User user =userMapper.selectById(userId);
        if (user != null) {
            // 创建会话记录
             ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();
            // 如果未提供标题
            if (StrUtil.isBlank(createDTO.getSessionTitle())) {
                session.setSessionTitle(String.format("宁渡AI助手 - " + DateUtil.format(LocalDateTime.now(), "MM-dd HH:mm")));
            }

            // 插入记录
            consultationSessionMapper.insert(session);
            return session;
        }

        return null;
    }

    /**
     * 查询某用户的会话列表（按开始时间倒序），带最后一条消息预览、消息数、时长
     */
    public PageResult<ConsultationSessionListItemDTO> listUserSessions(Long userId, Integer pageNum, Integer pageSize) {
        return listSessions(userId, pageNum, pageSize, false);
    }

    /**
     * 查询全部学生的会话列表（辅导员视角）。
     * <p>
     * 与 {@link #listUserSessions} 的区别有两点：
     * <ol>
     *   <li>不按用户过滤——辅导员需要看到所有学生的会话；</li>
     *   <li>补充学生姓名——否则列表上只有一堆「学生ID」，辅导员无从判断是谁。</li>
     * </ol>
     * <p>
     * <b>权限说明</b>：本方法会返回全体学生的会话，调用方必须已校验辅导员身份。
     * 当前为「辅导员可见全部学生」的简化实现；若后续需要按分管范围隔离，
     * 应在此处加入「辅导员 ↔ 学生」的关联过滤。
     */
    public PageResult<ConsultationSessionListItemDTO> listAllSessions(Integer pageNum, Integer pageSize) {
        return listSessions(null, pageNum, pageSize, true);
    }

    /**
     * 会话列表的统一实现。
     *
     * @param userId      为 null 表示不过滤用户（辅导员视角）
     * @param withUserName 是否补充学生姓名（仅辅导员视角需要，学生看自己的记录无需重复展示姓名）
     */
    private PageResult<ConsultationSessionListItemDTO> listSessions(Long userId, Integer pageNum,
                                                                    Integer pageSize, boolean withUserName) {
        int current = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null ? 10 : Math.min(pageSize, 50);
        Page<ConsultationSession> page = new Page<>(current, size);
        LambdaQueryWrapper<ConsultationSession> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(ConsultationSession::getUserId, userId);
        }
        wrapper.orderByDesc(ConsultationSession::getStartedAt)
                .orderByDesc(ConsultationSession::getId);
        consultationSessionMapper.selectPage(page, wrapper);

        // 批量取学生姓名：一次性查出来在内存里映射，避免逐条查询造成 N+1
        Map<Long, String> userNameMap = withUserName
                ? loadUserNames(page.getRecords())
                : Map.of();

        List<ConsultationSessionListItemDTO> items = page.getRecords().stream().map(session -> {
            ConsultationSessionListItemDTO dto = new ConsultationSessionListItemDTO();
            dto.setId(session.getId());
            dto.setSessionTitle(session.getSessionTitle());
            dto.setStartedAt(session.getStartedAt());
            dto.setUserId(session.getUserId());
            // 消息数与最后一条消息预览
            dto.setMessageCount(consultationMessageService.getMessageCountBySessionId(session.getId()));
            ConsultationMessageResponseDTO last = consultationMessageService.getLastMessageBySessionId(session.getId());
            dto.setLastMessageContent(last != null ? last.getContent() : null);
            // 会话时长（分钟）
            long minutes = 0;
            if (session.getStartedAt() != null) {
                minutes = Duration.between(session.getStartedAt(), LocalDateTime.now()).toMinutes();
            }
            dto.setDurationMinutes(minutes);
            if (withUserName) {
                dto.setUserName(userNameMap.get(session.getUserId()));
            }
            return dto;
        }).collect(Collectors.toList());

        return new PageResult<>(items, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** 批量查询会话归属学生的显示名 */
    private Map<Long, String> loadUserNames(List<ConsultationSession> sessions) {
        Set<Long> userIds = sessions.stream()
                .map(ConsultationSession::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getNickname() != null && !u.getNickname().isBlank()
                                ? u.getNickname() : u.getUsername(),
                        // 理论上 id 唯一不会冲突，兜底避免极端的重复键异常
                        (a, b) -> a));
    }

    /**
     * 按ID查询会话
     */
    public ConsultationSession getById(Long sessionId) {
        return consultationSessionMapper.selectById(sessionId);
    }

    /**
     * 写入情绪分析结果（JSON 字符串）
     * <p>
     * 由异步的情绪分析任务调用。MyBatis-Plus 的 updateById 默认忽略 null 字段，
     * 因此这里只更新这两个列，不会覆盖会话的其它属性。
     */
    public void updateEmotionAnalysis(Long sessionId, String analysisJson) {
        ConsultationSession update = ConsultationSession.builder()
                .id(sessionId)
                .lastEmotionAnalysis(analysisJson)
                .lastEmotionUpdatedAt(LocalDateTime.now())
                .build();
        consultationSessionMapper.updateById(update);
    }

    /**
     * 删除会话及其全部消息（事务保证一致性）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long userId, Long sessionId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        consultationMessageService.deleteBySessionId(sessionId);
        consultationSessionMapper.deleteById(sessionId);
    }
}
