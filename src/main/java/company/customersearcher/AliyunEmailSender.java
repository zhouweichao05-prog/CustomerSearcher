package company.customersearcher;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

public class AliyunEmailSender {
    // 阿里云邮箱SMTP配置
    private static final String SMTP_HOST = "smtp.qiye.aliyun.com";
    private static final int SMTP_PORT = 465; // SSL加密端口（推荐）
    // 你的阿里云企业邮箱账号（完整地址）
    private static final String SENDER_EMAIL = "elantor@ielantor.com";
    // 邮箱密码（开启二次验证则填客户端专用密码）
    private static final String SENDER_PASSWORD = "Y8GZAUbmzqA47Ukd";

    /**
     * 发送基础邮件
     * @param to 收件人邮箱（单个）
     * @param subject 邮件主题
     * @param content 邮件内容（支持HTML）
     * @param isHtml 是否为HTML格式
     */
    public static void sendEmail(String to, String subject, String content, boolean isHtml) {
        // 1. 配置SMTP属性
        Properties props = new Properties();
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        props.put("mail.smtp.ssl.enable", "true"); // 开启SSL加密
        props.put("mail.smtp.auth", "true"); // 开启身份验证

        // 2. 创建认证器
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        };

        // 3. 获取Session对象
        Session session = Session.getInstance(props, authenticator);
        session.setDebug(true); // 开启调试模式（可查看发送日志）

        try {
            // 4. 创建邮件消息
            MimeMessage message = new MimeMessage(session);
            // 设置发件人
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            // 设置收件人
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            // 设置主题
            message.setSubject(subject, "UTF-8");
            // 设置内容（文本/HTML）
            message.setContent(content, isHtml ? "text/html;charset=UTF-8" : "text/plain;charset=UTF-8");

            // 5. 发送邮件
            Transport.send(message);
            System.out.println("邮件发送成功！");
        } catch (MessagingException e) {
            System.err.println("邮件发送失败：" + e.getMessage());
            e.printStackTrace();
        }
    }

    // 测试方法
    public static void main(String[] args) {
        // 测试发送文本邮件
        sendEmail("3429265681@qq.com", "测试邮件", "这是来自阿里云企业邮箱的测试邮件", false);
        // 测试发送HTML邮件
        // sendEmail("customer@example.com", "HTML测试邮件", "<h1>标题</h1><p>这是HTML格式的邮件内容</p>", true);
    }
}
