package org.example.aispingboot.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * 向量化模型配置。
 * <p>
 * 背景：DeepSeek 未提供 Embedding 接口，语义检索必须另寻服务商。这里选用阿里云百炼的
 * {@code text-embedding-v3}（1024 维）。
 * <p>
 * 为什么不直接用 spring-ai-starter-model-openai 的自动配置：该 starter 的
 * {@code spring.ai.openai.*} 只能承载一份 api-key 与 base-url，而本项目
 * <b>对话走 DeepSeek、向量化走百炼</b>，两者密钥和端点都不同。因此这里手工装配一个
 * 独立的 EmbeddingModel Bean，与对话的 ChatModel 完全隔离、互不影响。
 */
@Configuration
public class EmbeddingConfig {

    @Value("${embedding.api-key:}")
    private String apiKey;

    @Value("${embedding.base-url}")
    private String baseUrl;

    @Bean
    public EmbeddingModel embeddingModel() {
        // 密钥缺失时明确失败：否则会退化成一个空 key 的 HTTP 401，
        // 排查时容易误以为是网络或配额问题
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "缺少向量化服务密钥：请设置环境变量 DASHSCOPE_API_KEY（阿里云百炼国际站）。"
                            + "语义检索依赖 Embedding 接口，未配置时无法启动。");
        }

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .embeddingsPath("/v1/embeddings")
                .build();

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model("text-embedding-v3")
                .dimensions(1024)
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.EMBED, options);
    }
}
