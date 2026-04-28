package org.example.aispingboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置。
 * <p>
 * 情绪分析属于「旁路增强能力」：它不该拖慢主对话的流式响应，也不能因为
 * 自己的失败影响用户聊天。因此放到独立线程池里异步执行。
 * <p>
 * 刻意不使用默认的 {@code ForkJoinPool.commonPool()}——它是 JVM 全局共享的，
 * 一旦这里的任务把队列堆满或线程阻塞，会连带影响其它使用 commonPool 的代码。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("aiAnalysisExecutor")
    public ThreadPoolTaskExecutor aiAnalysisExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        // 有界队列：拒绝无限制堆积，防止 LLM 变慢时任务积压打爆内存
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ai-analysis-");
        // 队列满时由调用线程执行，形成天然的背压，而不是静默丢弃任务
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 优雅停机：等在途的情绪分析落库完再退出，避免结果丢失
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
