package org.example.aispingboot.config;

import org.example.aispingboot.AiService.PromptManage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatClientConfig {
    // 会话记忆 ChatMemory 由 @Component RedisChatMemory 提供（Redis 实现），
    // 服务重启不丢、多实例可共享，这里无需再定义 Bean

    @Bean("open-ai")
    public ChatClient openAiChatClient(OpenAiChatModel openAiChatModel, ChatMemory chatMemory) {
        return ChatClient.builder(openAiChatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultSystem("你是一个专业的心理疏导师，温和耐心，善于倾听，能够提供专业的心理支持和建议").build();
    }

    /**
     * 对话摘要专用 ChatClient。
     * <p>
     * 用于分层记忆的长期层：当对话超出短期窗口后，把被裁掉的内容压缩成摘要。
     * <p>
     * 与对话客户端隔离的原因同上——摘要任务需要的是「忠实压缩」，
     * 与陪伴对话所需的温度、共情完全不同的取向；
     * 且它同样不需要记忆 Advisor（它处理的就是记忆本身，再挂记忆会递归）。
     */
    @Bean("conversation-summary")
    public ChatClient conversationSummaryChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(PromptManage.CONVERSATION_SUMMARY_SYSTEM_PROMPT)
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.2)
                        .build())
                .build();
    }

    /**
     * 情绪分析专用 ChatClient。
     * <p>
     * 与对话用的 "open-ai" 客户端刻意隔离，原因有三：
     * 1. 不能挂 MessageChatMemoryAdvisor——评估请求属于旁路调用，
     *    若共用记忆 Advisor 会把「评估指令」污染进用户的对话记忆；
     * 2. 使用独立的评估者人设，避免被「心理疏导师」人设带偏；
     * 3. temperature 设为 0，情绪评估需要稳定可复现，不能有随机性。
     */
    @Bean("emotion-analysis")
    public ChatClient emotionAnalysisChatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel)
                .defaultSystem(PromptManage.EMOTION_ANALYSIS_SYSTEM_PROMPT)
                .defaultOptions(OpenAiChatOptions.builder()
                        .temperature(0.0)
                        .build())
                .build();
    }
}
