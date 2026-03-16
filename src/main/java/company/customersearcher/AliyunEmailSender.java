package company.customersearcher;

import jakarta.activation.DataHandler;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Elantor Email Sender
 * Sends plain-text marketing emails with a PDF product catalog attachment.
 * Uses Alibaba Cloud Enterprise Mail (smtp.qiye.aliyun.com) via SSL.
 */
public class AliyunEmailSender {

    // ── SMTP Configuration ──────────────────────────────────────────────────
    private static final String SMTP_HOST       = "smtp.qiye.aliyun.com";
    private static final int    SMTP_PORT       = 465;
    private static final String SENDER_EMAIL    = "elantor@ielantor.com";
    private static final String SENDER_PASSWORD = "Y8GZAUbmzqA47Ukd";

    // ── Classpath Resource ───────────────────────────────────────────────────
    /** PDF catalog resource name (placed under src/main/resources/) */
    private static final String CATALOG_PDF = "Elantor_Product_Catalog.pdf";

    // ── Email Subject ────────────────────────────────────────────────────────
    private static final String EMAIL_SUBJECT =
            "Elantor | Professional ULV Cold Fogger Factory – Special Offer & Product Catalog";

    // ── Plain-text Email Body ────────────────────────────────────────────────
    private static final String EMAIL_BODY =
            "Dear Sir/Madam,\n\n"
            + "My name is Franklin Zhou, Sales Manager at Elantor Co., Ltd.\n\n"
            + "We are a professional manufacturer specializing in ULV Cold Foggers, "
            + "founded in 2017. With over 7 years of production experience, our products "
            + "have been exported to more than 50 countries worldwide and are trusted by "
            + "pest control professionals, agricultural operators, and public health agencies.\n\n"
            + "---\n\n"
            + "FEATURED PRODUCT: Electric ULV Cold Fogger – Model YF-500\n\n"
            + "This is our best-selling model, designed for efficient disinfection, "
            + "pest control, and chemical application across a wide range of environments.\n\n"
            + "Key Specifications:\n"
            + "  - Power:         1000W\n"
            + "  - Spray Range:   Up to 8 meters\n"
            + "  - Tank Capacity: 5 Liters\n"
            + "  - Particle Size: 10 – 150 μm (Adjustable)\n"
            + "  - Flow Rate:     470 ml/min\n"
            + "  - Net Weight:    2.35 kg\n"
            + "  - Voltage:       220V / 110V (Optional)\n"
            + "  - Certification: CE Certified\n\n"
            + "We are currently offering very competitive factory prices with support "
            + "for small-batch and custom orders. Whether you need a sample order or "
            + "bulk supply, we are happy to accommodate your requirements.\n\n"
            + "---\n\n"
            + "Please find our full product catalog attached to this email. "
            + "It covers our complete product range, including:\n"
            + "  - Electric ULV Cold Fogger (YF-500)\n"
            + "  - Thermal Fogger (TSF-35D)\n"
            + "  - Mini Mist Fogger (MF-100)\n"
            + "  - Portable Backpack Thermal Fogger\n"
            + "  - 5.6L Ultra-Low Volume Sprayer\n"
            + "  - Stainless Steel Backpack Pressure Sprayer (ZL-210A)\n\n"
            + "If you are interested in any of our products or would like to discuss "
            + "pricing, customization, or cooperation, please do not hesitate to contact us.\n\n"
            + "We look forward to hearing from you!\n\n"
            + "Best regards,\n"
            + "Franklin Zhou\n"
            + "Sales Manager | Elantor Co., Ltd.\n"
            + "Email:    info@elantor.com\n"
            + "WhatsApp: +86 19540736965\n"
            + "Website:  www.ielantor.com\n"
            + "Address:  Xing Business Building 310, Bulong Road, Bantian Street,\n"
            + "          Longgang District, Shenzhen, China 518118\n";

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
     * Send the Elantor marketing email: plain-text body + PDF catalog attachment.
     * The PDF is loaded from the classpath (src/main/resources/).
     *
     * @param to Recipient email address
     */
    public static void sendMarketingEmail(String to) {
        byte[] pdfBytes = loadResourceBytes(CATALOG_PDF);

        if (pdfBytes == null) {
            // No PDF found – send plain text only
            System.err.println("[WARN] PDF catalog not found on classpath: " + CATALOG_PDF
                    + ". Sending email without attachment.");
            sendEmail(to, EMAIL_SUBJECT, EMAIL_BODY, false);
            return;
        }

        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(EMAIL_SUBJECT, "UTF-8");

            // ── Plain-text body part ──
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(EMAIL_BODY, "text/plain;charset=UTF-8");

            // ── PDF attachment part (loaded from classpath) ──
            MimeBodyPart attachPart = new MimeBodyPart();
            ByteArrayDataSource pdfSource = new ByteArrayDataSource(pdfBytes, "application/pdf");
            attachPart.setDataHandler(new DataHandler(pdfSource));
            attachPart.setFileName(CATALOG_PDF);

            // ── Combine into multipart/mixed ──
            MimeMultipart multipart = new MimeMultipart("mixed");
            multipart.addBodyPart(textPart);
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
     * to prevent connection reset on larger attachments.
     */
    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              String.valueOf(SMTP_PORT));
        props.put("mail.smtp.ssl.enable",        "true");
        props.put("mail.smtp.auth",              "true");
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
        // ── Determine email list file path ──
        // Default: emails.txt in the working directory (project root when run from IDEA).
        // You can also pass a custom path as the first command-line argument:
        //   java -jar CustomerSearcher.jar /path/to/your/emails.txt
        String filePath = (args.length > 0) ? args[0] : "emails.txt";

        List<String> emails = readEmailsFromFile(filePath);
        if (emails.isEmpty()) {
            System.err.println("[WARN] No email addresses found in: " + Paths.get(filePath).toAbsolutePath());
            return;
        }

        System.out.println("[INFO] Loaded " + emails.size() + " email address(es) from: "
                + Paths.get(filePath).toAbsolutePath());

        int success = 0;
        int failure = 0;
        for (String email : emails) {
            try {
                sendMarketingEmail(email);
                success++;
                // Delay between sends to avoid rate limiting (30 seconds)
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[INFO] Done. Success: " + success + ", Failed: " + failure
                + ", Total: " + emails.size());
    }

    /**
     * Read email addresses from a plain-text file, one address per line.
     * Blank lines and lines starting with '#' (comments) are ignored.
     *
     * @param filePath Path to the email list file (absolute or relative to working directory)
     * @return List of valid email address strings
     */
    private static List<String> readEmailsFromFile(String filePath) {
        List<String> emails = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip blank lines and comment lines
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                emails.add(line);
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Cannot read email list file '" + filePath + "': " + e.getMessage());
        }
        return emails;
    }
}
