package org.example.aispingboot.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邮件通知。
 * <p>
 * <b>为什么用可降级设计</b>：邮件需要 SMTP 服务，而开发环境往往没有配置。
 * 如果启动时因为缺少 SMTP 配置就报错，会让「clone 下来就能跑」这件事失效。
 * <p>
 * 因此这里的行为是：
 * <ul>
 *   <li>配置了 {@code spring.mail.host} → 正常发送</li>
 *   <li>未配置 → 记录一条 WARN 日志后跳过，<b>不影响站内信与主业务</b></li>
 * </ul>
 * 这样项目既保留了完整的邮件能力，又不会因为环境缺失而无法运行。
 * <p>
 * <b>异步发送</b>：SMTP 是外部服务，同步发送会让建单接口等待网络往返。
 * 通知属于旁路能力，不应拖慢主链路。
 */
@Service
public class MailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

    /** 用 ObjectProvider 延迟获取：未引入邮件自动配置时不会导致启动失败 */
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    /** 是否具备发送能力（启动时判定一次） */
    private boolean enabled;

    public MailNotifier(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    @PostConstruct
    void init() {
        enabled = StringUtils.hasText(mailHost) && mailSenderProvider.getIfAvailable() != null;
        if (enabled) {
            log.info("邮件通知已启用，SMTP 主机：{}", mailHost);
        } else {
            log.warn("邮件通知未启用（未配置 spring.mail.host），将仅发送站内信。"
                    + "生产环境如需邮件触达，请配置 SMTP。");
        }
    }

    /** 是否已启用邮件渠道 */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 发送邮件。
     * <p>
     * 任何异常都只记录日志：邮件失败不应让调用方（如建单流程）受影响，
     * 且站内信已经留下记录，用户下次打开系统仍能看到。
     */
    @Async("aiAnalysisExecutor")
    public void send(String to, String subject, String content) {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(to)) {
            return;
        }
        try {
            JavaMailSender sender = mailSenderProvider.getIfAvailable();
            if (sender == null) {
                return;
            }
            SimpleMailMessage message = new SimpleMailMessage();
            // 部分 SMTP 服务要求发件人与认证账号一致，这里优先使用配置的账号
            if (StringUtils.hasText(mailFrom)) {
                message.setFrom(mailFrom);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            sender.send(message);
            log.info("邮件通知已发送至 {}", to);
        } catch (Exception e) {
            log.warn("邮件发送失败（不影响站内信与主业务）：to={}，原因：{}", to, e.getMessage());
        }
    }
}
