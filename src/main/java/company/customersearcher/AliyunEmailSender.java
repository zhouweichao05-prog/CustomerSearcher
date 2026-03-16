package company.customersearcher;

import jakarta.activation.DataHandler;
import jakarta.activation.DataSource;
import jakarta.activation.FileDataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Elantor Email Sender
 * Supports HTML marketing emails with PDF catalog attachment.
 * Uses Alibaba Cloud Enterprise Mail (smtp.qiye.aliyun.com) via SSL.
 */
public class AliyunEmailSender {

    // ── SMTP Configuration ──────────────────────────────────────────────────
    private static final String SMTP_HOST     = "smtp.qiye.aliyun.com";
    private static final int    SMTP_PORT     = 465;
    private static final String SENDER_EMAIL  = "elantor@ielantor.com";
    private static final String SENDER_PASSWORD = "Y8GZAUbmzqA47Ukd";

    // ── Email Assets (placed under src/main/resources/) ─────────────────────
    /** HTML template file name (on classpath) */
    private static final String HTML_TEMPLATE = "email_template.html";
    /** PDF catalog file path (absolute or relative to working directory) */
    private static final String CATALOG_PDF_PATH = "src/main/resources/Elantor_Product_Catalog.pdf";

    // ── Email Subject ────────────────────────────────────────────────────────
    private static final String EMAIL_SUBJECT =
            "Elantor | Professional ULV Cold Fogger Factory – Special Offer & Product Catalog";

    // ────────────────────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Send a plain-text or HTML email (no attachment).
     *
     * @param to      Recipient email address
     * @param subject Email subject
     * @param content Email body content
     * @param isHtml  true = HTML format, false = plain text
     */
    public static void sendEmail(String to, String subject, String content, boolean isHtml) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");
            message.setContent(content, isHtml ? "text/html;charset=UTF-8" : "text/plain;charset=UTF-8");
            Transport.send(message);
            System.out.println("[SUCCESS] Email sent to: " + to);
        } catch (MessagingException e) {
            System.err.println("[ERROR] Failed to send email to " + to + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send an HTML email with one file attachment.
     *
     * @param to             Recipient email address
     * @param subject        Email subject
     * @param htmlContent    HTML body content
     * @param attachmentPath Absolute or relative path to the attachment file
     * @param attachmentName Display name of the attachment (e.g. "Catalog.pdf")
     */
    public static void sendEmailWithAttachment(String to, String subject,
                                               String htmlContent,
                                               String attachmentPath,
                                               String attachmentName) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");

            // HTML body part
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlContent, "text/html;charset=UTF-8");

            // Attachment part
            MimeBodyPart attachPart = new MimeBodyPart();
            DataSource source = new FileDataSource(attachmentPath);
            attachPart.setDataHandler(new DataHandler(source));
            attachPart.setFileName(attachmentName);

            // Combine into multipart/mixed
            MimeMultipart multipart = new MimeMultipart("mixed");
            multipart.addBodyPart(htmlPart);
            multipart.addBodyPart(attachPart);

            message.setContent(multipart);
            Transport.send(message);
            System.out.println("[SUCCESS] Email with attachment sent to: " + to);
        } catch (MessagingException e) {
            System.err.println("[ERROR] Failed to send email to " + to + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send the pre-built Elantor marketing email (HTML + PDF catalog) to a recipient.
     * The HTML template and PDF catalog are loaded from the resources directory.
     *
     * @param to Recipient email address
     */
    public static void sendMarketingEmail(String to) {
        String htmlContent = loadHtmlTemplate();
        sendEmailWithAttachment(
                to,
                EMAIL_SUBJECT,
                htmlContent,
                CATALOG_PDF_PATH,
                "Elantor_Product_Catalog.pdf"
        );
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ────────────────────────────────────────────────────────────────────────

    /** Build and return an authenticated SMTP Session. */
    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host",       SMTP_HOST);
        props.put("mail.smtp.port",       SMTP_PORT);
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.auth",       "true");

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        };
        return Session.getInstance(props, auth);
    }

    /**
     * Load the HTML email template from the classpath.
     * Falls back to a minimal inline template if the file is not found.
     */
    private static String loadHtmlTemplate() {
        try (InputStream is = AliyunEmailSender.class
                .getClassLoader()
                .getResourceAsStream(HTML_TEMPLATE)) {
            if (is != null) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int bytesRead;
                while ((bytesRead = is.read(chunk)) != -1) {
                    buffer.write(chunk, 0, bytesRead);
                }
                return buffer.toString(StandardCharsets.UTF_8.name());
            }
        } catch (IOException e) {
            System.err.println("[WARN] Could not load HTML template: " + e.getMessage());
        }
        // Fallback inline template
        return "<html><body>"
                + "<h2>Elantor – Professional ULV Cold Fogger Factory</h2>"
                + "<p>Dear Customer,</p>"
                + "<p>We are <strong>Elantor Co., Ltd.</strong>, a professional manufacturer of ULV Cold Foggers "
                + "founded in 2017 with 7+ years of production experience.</p>"
                + "<p>Please find our product catalog attached. Contact us at "
                + "<a href='mailto:info@elantor.com'>info@elantor.com</a> for pricing and orders.</p>"
                + "<p>Best regards,<br/>Franklin Zhou | Sales Manager<br/>WhatsApp: +86 19540736965</p>"
                + "</body></html>";
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Entry point – example usage
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // ── Example 1: Send marketing email with catalog to a single customer ──
        sendMarketingEmail("customer@example.com");

        // ── Example 2: Send to multiple customers ──
        // String[] customers = {"buyer1@example.com", "buyer2@example.com"};
        // for (String email : customers) {
        //     sendMarketingEmail(email);
        // }

        // ── Example 3: Send plain-text email (legacy) ──
        // sendEmail("test@example.com", "Test", "Hello from Elantor!", false);
    }
}
