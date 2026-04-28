package org.example.aispingboot.controller;

import cn.hutool.json.JSONUtil;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.validation.Valid;
import org.example.aispingboot.AiService.PsychologicalSupportService;
import org.example.aispingboot.AiService.StructOutPut;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.command.ConsultationStreamDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.DTO.response.ConsultationSessionListItemDTO;
import org.example.aispingboot.common.PageResult;
import org.example.aispingboot.common.Result;
import org.example.aispingboot.common.ResultCode;
import org.example.aispingboot.entity.AgentTrace;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.exception.BusinessException;
import org.example.aispingboot.service.AgentTraceService;
import org.example.aispingboot.service.ConsultationMessageService;
import org.example.aispingboot.service.ConsultationSessionService;
import org.example.aispingboot.util.JwtTokenUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/psychological-chat")
public class PsychologicalChat {
    @Autowired
    private PsychologicalSupportService psychologicalSupportService;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private AgentTraceService agentTraceService;

    // 获取当前用户的会话列表（分页）
    @GetMapping("/sessions")
    public Result<PageResult<ConsultationSessionListItemDTO>> sessionList(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        Long userId = getCurrentUserId();
        return Result.ok(consultationSessionService.listUserSessions(userId, pageNum, pageSize));
    }

    // 获取会话的全部消息
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> sessionMessages(@PathVariable Long sessionId) {
        Long userId = getCurrentUserId();
        ConsultationSession session = consultationSessionService.getById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        return Result.ok(consultationMessageService.listBySessionId(sessionId));
    }

    // ==================== 辅导员视角 ====================
    // 与上面的学生接口刻意分开成独立路径，而不是在原接口里加角色分支：
    // 学生接口的语义是「我的会话」，辅导员接口是「全部学生的会话」，
    // 两者的数据范围与权限完全不同，混在一个接口里容易在后续改动中误放开权限。

    /** 全部学生的会话列表（仅辅导员） */
    @GetMapping("/counselor/sessions")
    public Result<PageResult<ConsultationSessionListItemDTO>> counselorSessionList(
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        checkCounselor();
        return Result.ok(consultationSessionService.listAllSessions(pageNum, pageSize));
    }

    /**
     * 查看指定会话的全部消息（仅辅导员）。
     * <p>
     * 辅导员查看学生对话是有必要的：判断如何介入必须了解学生说了什么。
     * 但这是极度敏感的数据，因此严格限定角色，且不作为管理员权限开放。
     */
    @GetMapping("/counselor/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> counselorSessionMessages(@PathVariable Long sessionId) {
        checkCounselor();
        ConsultationSession session = consultationSessionService.getById(sessionId);
        if (session == null) {
            throw new BusinessException("会话不存在");
        }
        return Result.ok(consultationMessageService.listBySessionId(sessionId));
    }

    /**
     * 查看指定会话的 Agent 执行轨迹（仅辅导员）。
     * <p>
     * 轨迹揭示了 AI 是「怎么得出这个回复的」：调用了哪些工具、拿到什么结果、耗时多久。
     * 对辅导员而言，这比只看对话内容更有价值——能判断 AI 的判断依据是否可靠。
     */
    @GetMapping("/counselor/sessions/{sessionId}/traces")
    public Result<List<AgentTrace>> counselorSessionTraces(@PathVariable Long sessionId) {
        checkCounselor();
        return Result.ok(agentTraceService.listBySession(sessionId));
    }

    /** 校验辅导员身份（roleType=3） */
    private void checkCounselor() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Integer roleType = jwt.getClaim("roleType").asInt();
        if (roleType == null || roleType != 3) {
            throw new BusinessException("无权限操作，仅辅导员可查看学生会话");
        }
    }

    // 删除会话（连同消息一起删除）
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        Long userId = getCurrentUserId();
        consultationSessionService.deleteSession(userId, sessionId);
        return Result.ok();
    }

    // 获取会话情绪分析（有历史分析则返回，否则返回默认中性情绪）
    @GetMapping("/session/{sessionId}/emotion")
    public Result<Map<String, Object>> sessionEmotion(@PathVariable String sessionId) {
        Long dbSessionId = psychologicalSupportService.extractSessionId(sessionId);
        if (dbSessionId == null) {
            throw new BusinessException("会话ID格式错误");
        }
        Long userId = getCurrentUserId();
        ConsultationSession session = consultationSessionService.getById(dbSessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException("会话不存在");
        }
        // 已有情绪分析结果直接返回
        if (StringUtils.hasText(session.getLastEmotionAnalysis())) {
            return Result.ok(JSONUtil.parseObj(session.getLastEmotionAnalysis()));
        }
        // 默认中性情绪
        Map<String, Object> defaultEmotion = new HashMap<>();
        defaultEmotion.put("primaryEmotion", "中性");
        defaultEmotion.put("emotionScore", 50);
        defaultEmotion.put("isNegative", false);
        defaultEmotion.put("riskLevel", 0);
        defaultEmotion.put("suggestion", "情绪状态平稳");
        defaultEmotion.put("improvementSuggestions", List.of());
        defaultEmotion.put("riskDescription", "");
        return Result.ok(defaultEmotion);
    }

    // 获取当前用户ID
    private Long getCurrentUserId() {
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        return jwt.getClaim("userId").asLong();
    }

    @PostMapping("/session/start")
    public Result<StructOutPut.StreamChatSession> startSession(@Valid @RequestBody ConsultationSessionCreateDTO createDTO) {
        // 获取当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();
        StructOutPut.StreamChatSession session = psychologicalSupportService.startSession(userId, createDTO);
        return Result.ok(session);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody ConsultationStreamDTO streamDTO) {
        // 获取当前用户
        String token = JwtTokenUtil.getCurrentToken();
        DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
        Long userId = jwt.getClaim("userId").asLong();

        if (userId == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .event("error")
                    .data(JSONUtil.toJsonStr(Result.error(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg(), "用户未登录")))
                    .build());
        }

        // 开始流式对话
        return psychologicalSupportService.streamPsychologicalChat(streamDTO.getSessionId(), streamDTO.getUserMessage())
                .map(Fragment -> {
                    return ServerSentEvent.<String>builder()
                            .event("message")
                            .data(JSONUtil.toJsonStr(Result.ok(Map.of("content", Fragment, "type", "normal"))))
                            .build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder()
                        .event("done")
                        .data("{}")
                        .build()
                ))
                .delayElements(Duration.ofMillis(50)); // 添加延迟确保流式数据的体验
    }
}
