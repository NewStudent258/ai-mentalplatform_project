package org.example.aispingboot.AiService;

import org.example.aispingboot.AiService.tool.AssessmentTriggerTool;
import org.example.aispingboot.AiService.tool.EscalateToHumanTool;
import org.example.aispingboot.AiService.tool.KnowledgeSearchTool;
import org.example.aispingboot.AiService.tool.MoodHistoryTool;
import org.example.aispingboot.AiService.tool.PracticeRecommendTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.example.aispingboot.DTO.command.ConsultationSessionCreateDTO;
import org.example.aispingboot.DTO.response.ConsultationMessageResponseDTO;
import org.example.aispingboot.entity.ConsultationSession;
import org.example.aispingboot.service.AgentTraceService;
import org.example.aispingboot.service.ConsultationMessageService;
import org.example.aispingboot.service.ConsultationSessionService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class PsychologicalSupportService {

    private static final Logger log = LoggerFactory.getLogger(PsychologicalSupportService.class);

    @Autowired
    @Qualifier("open-ai")
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private ConsultationSessionService consultationSessionService;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    @Autowired
    private EmotionAnalysisService emotionAnalysisService;

    @Autowired
    private KnowledgeSearchTool knowledgeSearchTool;

    /** 情绪历史查询工具：让 AI 能基于真实记录回答「我最近怎么样」 */
    @Autowired
    private MoodHistoryTool moodHistoryTool;

    /** 自助练习推荐工具：从预置练习库中选择，不自行编写练习步骤 */
    @Autowired
    private PracticeRecommendTool practiceRecommendTool;

    /** 转介人工工具：Agent 判断需要真人介入时主动建单 */
    @Autowired
    private EscalateToHumanTool escalateToHumanTool;

    /** 测评触发工具：Agent 只决定时机，题目与计分由服务端负责 */
    @Autowired
    private AssessmentTriggerTool assessmentTriggerTool;

    /** 安全护栏：对用药、诊断、自伤等受限话题，改用预置话术而非模型自由生成 */
    @Autowired
    private SafetyGuard safetyGuard;

    /** Agent 执行轨迹：记录本轮的工具调用与生成过程，供事后回看与问题定位 */
    @Autowired
    private AgentTraceService agentTraceService;

    @Transactional(rollbackFor = Exception.class)
    public StructOutPut.StreamChatSession startSession(Long userId, ConsultationSessionCreateDTO createDTO) {
        // 创建数据库会话记录
        ConsultationSession consultationSession = consultationSessionService.createSession(userId, createDTO);

        // 将初始用户消息保存到Message表
        consultationMessageService.saveUserMessage(consultationSession.getId(), createDTO.getInitialMessage(), null);

        // 创建会话信息
        String sessionId = "session_" + consultationSession.getId();
        return new StructOutPut.StreamChatSession(
                sessionId,
                userId,
                createDTO.getInitialMessage(),
                System.currentTimeMillis(),
                System.currentTimeMillis() + 86400000L, // 24小时
                1,
                "ACTIVE"
        );
    }

    public Flux<String> streamPsychologicalChat(String sessionId, String userMessage) {
        // 创建响应流
        return Flux.create(sink -> {
            // sink.next("数据1") // 发布数据
            // sink.complete(); // 完成流
            // sink.error(exception); // 发布错误
            Long dbSessionId = extractSessionId(sessionId);
            if (dbSessionId == null) {
                sink.error(new RuntimeException("会话ID格式错误"));
                return;
            }
            // 是否为初始消息
            boolean isInitialMessage = false;
            // 检查是否为初始消息，避免重复保存
            Integer messageCount = consultationMessageService.getMessageCountBySessionId(dbSessionId);
            if (messageCount == 1) {
               ConsultationMessageResponseDTO lastMessage = consultationMessageService.getLastMessageBySessionId(dbSessionId);
                if (lastMessage != null && lastMessage.getSenderType() == 1 && userMessage.equals(lastMessage.getContent())) {
                    isInitialMessage = true;
                }
            }
            if (!isInitialMessage) {
                // 保存用户消息到数据库
                consultationMessageService.saveUserMessage(dbSessionId, userMessage, null);
            }

            // 情绪分析走独立线程池异步执行，与下面的流式生成并行。
            // 放在这里（而不是等 AI 回复结束后）有两个好处：
            // 1. 分析的是「用户的情绪」，与 AI 回复内容无关，本就不需要等待生成结果；
            // 2. 大模型生成通常要几秒，而分析更快，等流结束时结果早已落库，
            //    前端在 done 事件里回查情绪时就能拿到最新值，不给用户增加等待。
            emotionAnalysisService.analyzeAndSave(dbSessionId, userMessage);

            // 生成对话记忆管理
            String conversationId = "conversation_" + sessionId;

            // ===== 开启 Agent 执行轨迹 =====
            // 本轮对话中 Agent 的每一次工具调用与生成都会挂在这个轮次下，
            // 供事后回看「它到底做了什么」。放在最前面，确保安全拦截路径也能被记录。
            ConsultationSession session = consultationSessionService.getById(dbSessionId);
            Long ownerId = session == null ? null : session.getUserId();
            Map<String, Object> traceContext = agentTraceService.startTurn(dbSessionId, ownerId, userMessage);

            // ===== 安全护栏：受限话题不交由模型自由生成 =====
            // 放在情绪分析之后：即使用户触及受限话题，其情绪与风险仍要照常记录，
            // 否则「我想自杀」这类最该被记录的消息反而不入库。
            Optional<SafetyGuard.Category> restricted = safetyGuard.detect(userMessage);
            if (restricted.isPresent()) {
                SafetyGuard.Category category = restricted.get();
                safetyGuard.logHit(category, "chat-stream");
                String safeResponse = safetyGuard.response(category);

                // 拦截本身也是一条重要的执行轨迹：事后需要确认「这类话题确实没走模型」
                agentTraceService.recordSafetyBlock(traceContext, category.name(),
                        "命中受限话题，返回预置安全话术，未交由模型生成");

                // 仍然走流式返回，前端无需区分两种来源
                sink.next(safeResponse);
                // 落库与写入记忆：保证会话记录完整、下一轮上下文一致
                consultationMessageService.saveAimessage(dbSessionId, safeResponse, "safety-guard");
                List<Message> safeMessages = new ArrayList<>();
                safeMessages.add(new AssistantMessage(safeResponse));
                chatMemory.add(conversationId, safeMessages);

                log.info("会话 {} 命中安全护栏（{}），已返回预置话术", dbSessionId, category);
                sink.complete();
                return;
            }

            // 构建系统提示词
            List<Message> userMessages = new ArrayList<>();
            userMessages.add(new UserMessage(userMessage));
            chatMemory.add(conversationId, userMessages);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT)
            ));

            //用于存储AI完成的响应
            StringBuilder fullResponse = new StringBuilder();

            // 用户是否中途停止了本次回复（前端 abort 后由 onCancel 置位）
            AtomicBoolean stopped = new AtomicBoolean(false);

            // 记录生成耗时：从发起请求到流结束
            long generationStart = System.currentTimeMillis();

            // 使用chatClient发送消息到Open AI
            Disposable disposable = chatClient.prompt(prompt)
                    .user(userMessage)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                    // 传入轮次上下文：ToolTraceAspect 会从中取出轮次信息，
                    // 从而把本轮内所有工具调用归集到同一条执行轨迹下
                    .toolContext(traceContext)
                    // 注册工具集：模型会在需要时自主决定调用哪个，
                    // Spring AI 内部完成「调用工具 -> 把结果回灌 -> 继续生成」的闭环。
                    // 多个工具意味着 Agent 需要自己判断调用顺序，而非固定走某一条链路。
                    .tools(knowledgeSearchTool, moodHistoryTool, practiceRecommendTool,
                            escalateToHumanTool, assessmentTriggerTool)
                    .stream()
                    .content()
                    .doOnNext(Fragment -> {
                        fullResponse.append(Fragment);
                        sink.next(Fragment);
                    })
                    // 连接被重置等传输层错误时自动重试（最多2次）
                    .retryWhen(Retry.backoff(2, Duration.ofMillis(800))
                            .filter(error -> error instanceof WebClientRequestException)
                            .doBeforeRetry(retrySignal -> fullResponse.setLength(0)))
                    .doOnComplete(() -> {
                        String completeRes = fullResponse.toString();
                        // 将AI返回的内容保存到数据库
                        consultationMessageService.saveAimessage(dbSessionId, completeRes, "openai");
                        // 添加AI回复到chatMemory
                        List<Message> aiMessages = new ArrayList<>();
                        aiMessages.add(new AssistantMessage(completeRes));
                        chatMemory.add(conversationId, aiMessages);

                        // 记录本轮生成步骤，至此一轮完整轨迹闭合：
                        // 轮次开始 → 若干次工具调用 → 内容生成
                        agentTraceService.recordGeneration(traceContext,
                                "生成回复 " + completeRes.length() + " 字",
                                System.currentTimeMillis() - generationStart);

                        sink.complete();
                    })
                    .doOnError(error -> {
                        sink.error(error);
                    })
                    .subscribe(); // 订阅并启动流

            // 前端点击"停止"后连接会关闭，Reactor 触发 cancel。
            // 必须在这里 dispose 上游订阅，否则 LLM 请求会继续跑完——既浪费 token，
            // 又会让 doOnComplete 把用户根本没看到的完整回复写进数据库。
            sink.onCancel(() -> {
                // 标记为已停止，避免下面的兜底逻辑与 doOnComplete 竞态重复落库
                stopped.set(true);
                disposable.dispose();

                // 用户已看到的部分仍保留，并回写 chatMemory，
                // 这样下一轮对话的上下文与用户实际看到的内容一致，不会"答非所问"
                String partial = fullResponse.toString();
                if (!partial.isEmpty()) {
                    List<Message> aiMessages = new ArrayList<>();
                    aiMessages.add(new AssistantMessage(partial));
                    chatMemory.add(conversationId, aiMessages);
                }
            });
        });
    }

    // 获取参数中的sessionId
    public Long extractSessionId(String sessionId) {
        if (sessionId != null && sessionId.startsWith("session_")) {
            String idStr = sessionId.substring("session_".length());
            return Long.parseLong(idStr);
        }
        return null;
    }
}
