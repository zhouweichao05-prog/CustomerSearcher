package company.customersearcher;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Elantor Email Sender
 * Supports HTML marketing emails with PDF catalog attachment.
 * Uses Alibaba Cloud Enterprise Mail (smtp.qiye.aliyun.com) via SSL.
 *
 * Both the HTML template and PDF catalog are loaded from the classpath
 * (src/main/resources/), so they work correctly both in IDE and after
 * Maven packaging.
 */
public class AliyunEmailSender {

    // ── SMTP Configuration ──────────────────────────────────────────────────
    private static final String SMTP_HOST      = "smtp.qiye.aliyun.com";
    private static final int    SMTP_PORT      = 465;
    private static final String SENDER_EMAIL   = "elantor@ielantor.com";
    private static final String SENDER_PASSWORD = "Y8GZAUbmzqA47Ukd";

    // ── Classpath Resources (under src/main/resources/) ─────────────────────
    /** HTML template resource name on classpath */
    private static final String HTML_TEMPLATE  = "email_template.html";
    /** PDF catalog resource name on classpath */
    private static final String CATALOG_PDF    = "Elantor_Product_Catalog.pdf";

    // ── Email Subject ────────────────────────────────────────────────────────
    private static final String EMAIL_SUBJECT  =
            "Elantor | Professional ULV Cold Fogger Factory \u2013 Special Offer & Product Catalog";

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
     * Send the pre-built Elantor marketing email (HTML body + PDF catalog attachment).
     * Both resources are loaded from the classpath.
     *
     * @param to Recipient email address
     */
    public static void sendMarketingEmail(String to) {
        String htmlContent = loadResource(HTML_TEMPLATE);
        byte[] pdfBytes    = loadResourceBytes(CATALOG_PDF);

        if (pdfBytes == null) {
            System.err.println("[WARN] PDF catalog not found on classpath: " + CATALOG_PDF
                    + ". Sending email without attachment.");
            sendEmail(to, EMAIL_SUBJECT, htmlContent, true);
            return;
        }

        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(EMAIL_SUBJECT, "UTF-8");

            // ── HTML body part ──
            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(htmlContent, "text/html;charset=UTF-8");

            // ── PDF attachment part (loaded from classpath bytes) ──
            MimeBodyPart attachPart = new MimeBodyPart();
            ByteArrayDataSource pdfSource = new ByteArrayDataSource(pdfBytes, "application/pdf");
            attachPart.setDataHandler(new DataHandler(pdfSource));
            attachPart.setFileName(CATALOG_PDF);

            // ── Combine into multipart/mixed ──
            MimeMultipart multipart = new MimeMultipart("mixed");
            multipart.addBodyPart(htmlPart);
            multipart.addBodyPart(attachPart);

            message.setContent(multipart);
            Transport.send(message);
            System.out.println("[SUCCESS] Marketing email with catalog sent to: " + to);
        } catch (MessagingException e) {
            System.err.println("[ERROR] Failed to send email to " + to + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Build and return an authenticated SMTP Session with timeout settings
     * to prevent connection reset on large attachments.
     */
    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              String.valueOf(SMTP_PORT));
        props.put("mail.smtp.ssl.enable",        "true");
        props.put("mail.smtp.auth",              "true");
        // Increase timeouts to handle larger attachments (milliseconds)
        props.put("mail.smtp.connectiontimeout", "30000");  // 30s connect timeout
        props.put("mail.smtp.timeout",           "60000");  // 60s read timeout
        props.put("mail.smtp.writetimeout",      "60000");  // 60s write timeout

        Authenticator auth = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        };
        return Session.getInstance(props, auth);
    }

    /**
     * Load a classpath resource as a UTF-8 String.
     * Falls back to a minimal inline HTML template if the resource is not found.
     */
    private static String loadResource(String resourceName) {
        byte[] bytes = loadResourceBytes(resourceName);
        if (bytes != null) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        System.err.println("[WARN] Resource not found on classpath: " + resourceName + ". Using fallback template.");
        return "<html><body>"
                + "<h2>Elantor \u2013 Professional ULV Cold Fogger Factory</h2>"
                + "<p>Dear Customer,</p>"
                + "<p>We are <strong>Elantor Co., Ltd.</strong>, a professional manufacturer of ULV Cold Foggers "
                + "founded in 2017 with 7+ years of production experience.</p>"
                + "<p>Please find our product catalog attached. Contact us at "
                + "<a href='mailto:info@elantor.com'>info@elantor.com</a> for pricing and orders.</p>"
                + "<p>Best regards,<br/>Franklin Zhou | Sales Manager<br/>WhatsApp: +86 19540736965</p>"
                + "</body></html>";
    }

    /**
     * Load a classpath resource as a raw byte array.
     * Returns null if the resource is not found.
     */
    private static byte[] loadResourceBytes(String resourceName) {
        try (InputStream is = AliyunEmailSender.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (is == null) {
                return null;
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesRead);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            System.err.println("[WARN] Failed to read resource " + resourceName + ": " + e.getMessage());
            return null;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Entry point – example usage
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // ── Send marketing email with PDF catalog to a single customer ──
        sendMarketingEmail("3429265681@qq.com");

        // ── Send to multiple customers ──
        // String[] customers = {"buyer1@example.com", "buyer2@example.com"};
        // for (String email : customers) {
        //     sendMarketingEmail(email);
        // }
    }
}
