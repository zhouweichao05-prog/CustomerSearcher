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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

/**
 * Elantor Email Sender
 * Sends plain-text marketing emails with a PDF product catalog attachment.
 * Uses Alibaba Cloud Enterprise Mail (smtp.qiye.aliyun.com) via SSL.
 *
 * Anti-spam optimizations (v1.2):
 *   1. Randomized send interval (60-120 s) to avoid fixed-pattern detection.
 *   2. Daily send cap (MAX_DAILY_SENDS) to stay within Alibaba Cloud limits.
 *   3. Business-hours guard: only sends between 08:00-18:00 local time.
 *   4. Personalized greeting per recipient (uses local-part of email address).
 *   5. Removed non-ASCII characters (em dash, mu symbol) from subject/body.
 *   6. Added Message-ID and Date headers for RFC compliance.
 *   7. Failure counter: stops sending if consecutive failures exceed threshold.
 */
public class AliyunEmailSender {

    // ── SMTP Configuration ──────────────────────────────────────────────────
    private static final String SMTP_HOST       = "smtp.qiye.aliyun.com";
    private static final int    SMTP_PORT       = 465;
    private static final String SENDER_EMAIL    = "elantor@ielantor.com";
    private static final String SENDER_PASSWORD = "Y8GZAUbmzqA47Ukd";

    // ── Classpath Resource ───────────────────────────────────────────────────
    private static final String CATALOG_PDF = "Elantor_Product_Catalog.pdf";

    // ── Anti-spam send control ───────────────────────────────────────────────
    /** Minimum delay between sends (ms). 60 s keeps well under per-minute limits. */
    private static final int    SEND_INTERVAL_MIN_MS  = 60_000;
    /** Maximum delay between sends (ms). Random jitter avoids pattern detection. */
    private static final int    SEND_INTERVAL_MAX_MS  = 120_000;
    /** Maximum emails to send per run. Alibaba Cloud standard plan: ~2500/day/account.
     *  Set conservatively to 100 per run to avoid triggering daily-limit checks. */
    private static final int    MAX_DAILY_SENDS       = 100;
    /** Stop sending after this many consecutive failures (likely rate-limited). */
    private static final int    MAX_CONSECUTIVE_FAILS = 5;
    /** Only send during business hours (inclusive). Reduces spam-score risk. */
    private static final int    SEND_HOUR_START       = 8;
    private static final int    SEND_HOUR_END         = 18;

    // ── Email Subject ────────────────────────────────────────────────────────
    // NOTE: Non-ASCII characters (em dash) removed to avoid encoding-related
    //       spam triggers on some receiving mail servers.
    private static final String EMAIL_SUBJECT =
            "Elantor | Professional ULV Cold Fogger Factory - Special Offer & Product Catalog";

    // ── Plain-text Email Body template ───────────────────────────────────────
    // Use {NAME} as placeholder; replaced per-recipient with a personalized greeting.
    private static final String EMAIL_BODY_TEMPLATE =
            "Dear {NAME},\n\n"
            + "My name is Franklin Zhou, Sales Manager at Elantor Co., Ltd.\n\n"
            + "We are a professional manufacturer specializing in ULV Cold Foggers, "
            + "founded in 2017. With over 7 years of production experience, our products "
            + "have been exported to more than 50 countries worldwide and are trusted by "
            + "pest control professionals, agricultural operators, and public health agencies.\n\n"
            + "---\n\n"
            + "FEATURED PRODUCT: Electric ULV Cold Fogger - Model YF-500\n\n"
            + "This is our best-selling model, designed for efficient disinfection, "
            + "pest control, and chemical application across a wide range of environments.\n\n"
            + "Key Specifications:\n"
            + "  - Power:         1000W\n"
            + "  - Spray Range:   Up to 8 meters\n"
            + "  - Tank Capacity: 5 Liters\n"
            + "  - Particle Size: 10 - 150 um (Adjustable)\n"
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

    // ── Pre-loaded PDF bytes (loaded once at startup) ────────────────────────
    private static final byte[] PDF_BYTES = loadResourceBytes(CATALOG_PDF);

    private static final Random RNG = new Random();

    // ────────────────────────────────────────────────────────────────────────
    //  Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Send a plain-text or HTML email (no attachment).
     */
    public static void sendEmail(String to, String subject, String content, boolean isHtml) {
        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(subject, "UTF-8");
            message.setSentDate(new java.util.Date());
            message.setContent(content, isHtml ? "text/html;charset=UTF-8" : "text/plain;charset=UTF-8");
            Transport.send(message);
            System.out.println("[SUCCESS] Email sent to: " + to);
        } catch (MessagingException e) {
            System.err.println("[ERROR] Failed to send email to " + to + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send the Elantor marketing email: personalized plain-text body + PDF attachment.
     */
    public static void sendMarketingEmail(String to) {
        // Personalize greeting: extract local-part of email as recipient name hint
        String name = extractName(to);
        String body = EMAIL_BODY_TEMPLATE.replace("{NAME}", name);

        if (PDF_BYTES == null) {
            System.err.println("[WARN] PDF catalog not found on classpath: " + CATALOG_PDF
                    + ". Sending email without attachment.");
            sendEmail(to, EMAIL_SUBJECT, body, false);
            return;
        }

        Session session = createSession();
        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            message.setSubject(EMAIL_SUBJECT, "UTF-8");
            // RFC 2822 compliant Date header – missing date header raises spam score
            message.setSentDate(new java.util.Date());

            // ── Plain-text body part ──
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setContent(body, "text/plain;charset=UTF-8");

            // ── PDF attachment part ──
            MimeBodyPart attachPart = new MimeBodyPart();
            ByteArrayDataSource pdfSource = new ByteArrayDataSource(PDF_BYTES, "application/pdf");
            attachPart.setDataHandler(new DataHandler(pdfSource));
            attachPart.setFileName(CATALOG_PDF);

            // ── Combine into multipart/mixed ──
            MimeMultipart multipart = new MimeMultipart("mixed");
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(attachPart);

            message.setContent(multipart);
            Transport.send(message);
            System.out.println("[SUCCESS] Marketing email sent to: " + to + " (greeting: " + name + ")");
        } catch (MessagingException e) {
            System.err.println("[ERROR] Failed to send email to " + to + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e); // re-throw so caller can count failures
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Private helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Extract a human-readable name hint from an email address local-part.
     * e.g. "info@example.com" -> "Sir/Madam"
     *      "john.doe@example.com" -> "John Doe"
     *      "sales@example.com" -> "Sir/Madam"  (generic role accounts)
     */
    private static final java.util.Set<String> GENERIC_PREFIXES = new java.util.HashSet<>(
            java.util.Arrays.asList("info", "sales", "contact", "admin", "support",
                    "enquiry", "enquiries", "mail", "office", "hello", "hi",
                    "service", "marketing", "general", "pest", "ridpest", "pestfree"));

    private static String extractName(String email) {
        String local = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
        if (GENERIC_PREFIXES.contains(local.toLowerCase())) {
            return "Sir/Madam";
        }
        // Convert dots/underscores/hyphens to spaces and title-case each word
        String[] parts = local.split("[._\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) sb.append(part.substring(1).toLowerCase());
        }
        String name = sb.toString().trim();
        return name.isEmpty() ? "Sir/Madam" : name;
    }

    /**
     * Build and return an authenticated SMTP Session.
     */
    private static Session createSession() {
        Properties props = new Properties();
        props.put("mail.smtp.host",              SMTP_HOST);
        props.put("mail.smtp.port",              String.valueOf(SMTP_PORT));
        props.put("mail.smtp.ssl.enable",        "true");
        props.put("mail.smtp.auth",              "true");
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.timeout",           "60000");
        props.put("mail.smtp.writetimeout",      "60000");

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
     */
    private static byte[] loadResourceBytes(String resourceName) {
        try (InputStream is = AliyunEmailSender.class
                .getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (is == null) return null;
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

    /**
     * Return a random delay between SEND_INTERVAL_MIN_MS and SEND_INTERVAL_MAX_MS.
     * Random jitter prevents the fixed-interval pattern that spam filters flag.
     */
    private static int randomDelay() {
        return SEND_INTERVAL_MIN_MS
                + RNG.nextInt(SEND_INTERVAL_MAX_MS - SEND_INTERVAL_MIN_MS + 1);
    }

    /**
     * Check whether the current local time is within allowed sending hours.
     */
    private static boolean isBusinessHour() {
        int hour = LocalTime.now().getHour();
        return hour >= SEND_HOUR_START && hour < SEND_HOUR_END;
    }

    // ────────────────────────────────────────────────────────────────────────
    //  Entry point
    // ────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        String filePath = (args.length > 0) ? args[0] : "emails.txt";

        List<String> emails = readEmailsFromFile(filePath);
        if (emails.isEmpty()) {
            System.err.println("[WARN] No email addresses found in: " + Paths.get(filePath).toAbsolutePath());
            return;
        }

        System.out.println("[INFO] Loaded " + emails.size() + " email address(es) from: "
                + Paths.get(filePath).toAbsolutePath());
        System.out.println("[INFO] PDF attachment: "
                + (PDF_BYTES != null ? String.format("%.1f KB", PDF_BYTES.length / 1024.0) : "NOT FOUND"));
        System.out.println("[INFO] Daily cap: " + MAX_DAILY_SENDS + " emails per run");
        System.out.println("[INFO] Send interval: " + SEND_INTERVAL_MIN_MS / 1000
                + "-" + SEND_INTERVAL_MAX_MS / 1000 + " s (randomized)");

        int success = 0;
        int failure = 0;
        int consecutiveFails = 0;

        for (int i = 0; i < emails.size(); i++) {
            // Daily cap guard
            if (success >= MAX_DAILY_SENDS) {
                System.out.println("[INFO] Daily send cap (" + MAX_DAILY_SENDS
                        + ") reached. Stopping for today. Remaining: " + (emails.size() - i));
                break;
            }

            // Consecutive failure guard (likely rate-limited by server)
            if (consecutiveFails >= MAX_CONSECUTIVE_FAILS) {
                System.err.println("[WARN] " + MAX_CONSECUTIVE_FAILS
                        + " consecutive failures detected. Possible rate-limit. Stopping.");
                break;
            }

            // Business-hours guard
            if (!isBusinessHour()) {
                System.out.println("[INFO] Outside business hours (" + SEND_HOUR_START + ":00-"
                        + SEND_HOUR_END + ":00). Waiting 10 minutes...");
                try { Thread.sleep(600_000); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); break;
                }
                i--; // retry same email after waiting
                continue;
            }

            String email = emails.get(i);
            try {
                sendMarketingEmail(email);
                success++;
                consecutiveFails = 0; // reset on success

                if (i < emails.size() - 1) {
                    int delay = randomDelay();
                    System.out.println("[INFO] Next send in " + delay / 1000 + " s...");
                    Thread.sleep(delay);
                }
            } catch (Exception e) {
                failure++;
                consecutiveFails++;
                System.err.println("[ERROR] Send failed for " + email
                        + " (consecutive fails: " + consecutiveFails + ")");
                // Short back-off before retrying next address
                try { Thread.sleep(30_000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt(); break;
                }
            }
        }

        System.out.println("[INFO] Done. Success: " + success
                + ", Failed: " + failure + ", Total: " + emails.size());
    }

    /**
     * Read email addresses from a plain-text file, one address per line.
     */
    private static List<String> readEmailsFromFile(String filePath) {
        List<String> emails = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                emails.add(line);
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Cannot read email list file '" + filePath + "': " + e.getMessage());
        }
        return emails;
    }
}
