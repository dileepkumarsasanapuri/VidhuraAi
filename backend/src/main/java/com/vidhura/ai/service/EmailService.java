package com.vidhura.ai.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Service responsible for dispatching real transactional emails (OTP, password reset)
 * via SMTP with automated fallback to simulated delivery logging.
 */
@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:${SMTP_USERNAME:}}")
    private String mailUsername;

    @Value("${spring.mail.from:${SMTP_FROM:no-reply@vidhura.ai}}")
    private String mailFrom;

    /**
     * Sends a responsive, branded HTML password reset email with the 6-digit OTP code.
     *
     * @param toEmail The destination email address
     * @param otp     The 6-digit OTP code
     * @return true if sent via SMTP, false if delivered via simulated logger
     */
    public boolean sendPasswordResetOtpEmail(String toEmail, String otp) {
        if (toEmail == null || otp == null) {
            return false;
        }

        String recipient = toEmail.toLowerCase().trim();

        // 1. Attempt real SMTP delivery if mailSender & credentials are configured
        if (mailSender != null && mailUsername != null && !mailUsername.trim().isEmpty()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                String fromAddress = (mailFrom != null && !mailFrom.trim().isEmpty()) ? mailFrom : mailUsername;
                helper.setFrom(fromAddress, "Vidhura AI Security");
                helper.setTo(recipient);
                helper.setSubject("Your Vidhura AI Password Reset Code: " + otp);

                String htmlContent = buildOtpHtmlTemplate(otp);
                helper.setText(htmlContent, true);

                mailSender.send(message);
                log.info("[EmailService] ✉️ Successfully sent real transactional email with OTP to: {}", recipient);
                return true;
            } catch (Exception e) {
                log.error("[EmailService] ⚠️ SMTP transport error delivering email to {}: {}", recipient, e.getMessage());
                // Fall through to simulated console delivery
            }
        }

        // 2. Clear production console logging if SMTP credentials are not yet supplied in .env
        log.info("==========================================================================================");
        log.info("[PRODUCTION EMAIL CARRIER] Transactional Email Ready for: {}", recipient);
        log.info("📧 To: {}", recipient);
        log.info("📌 Subject: Your Vidhura AI Password Reset Code: {}", otp);
        log.info("🔑 Verification OTP Code: {}", otp);
        log.info("⏱️ Expiry: 10 minutes");
        if (mailUsername == null || mailUsername.trim().isEmpty()) {
            log.info("💡 Note: To send actual emails directly to your Gmail/inbox, provide SMTP credentials in .env:");
            log.info("   SMTP_HOST=smtp.gmail.com");
            log.info("   SMTP_PORT=587");
            log.info("   SMTP_USERNAME=your-email@gmail.com");
            log.info("   SMTP_PASSWORD=your-16-char-app-password");
        }
        log.info("==========================================================================================");

        return false;
    }

    private String buildOtpHtmlTemplate(String otp) {
        return "<!DOCTYPE html>"
                + "<html>"
                + "<head><meta charset='UTF-8'></head>"
                + "<body style='font-family: -apple-system, BlinkMacSystemFont, Segoe UI, Roboto, sans-serif; background-color: #030712; color: #ffffff; padding: 24px; margin: 0;'>"
                + "  <div style='max-width: 520px; margin: 0 auto; background: #0f172a; border-radius: 20px; border: 1px solid #1e293b; padding: 36px; box-shadow: 0 10px 30px rgba(0,0,0,0.6);'>"
                + "    <div style='text-align: center; margin-bottom: 28px;'>"
                + "      <h1 style='color: #38bdf8; font-size: 26px; margin: 0; font-weight: 800;'>🛡️ Vidhura AI</h1>"
                + "      <p style='color: #94a3b8; font-size: 12px; margin-top: 4px; letter-spacing: 0.5px;'>INSURANCE POLICY INTELLIGENCE</p>"
                + "    </div>"
                + "    <h2 style='font-size: 20px; color: #f8fafc; margin-bottom: 12px;'>Password Reset Request</h2>"
                + "    <p style='color: #cbd5e1; font-size: 14px; line-height: 1.6; margin-bottom: 24px;'>"
                + "      We received a request to reset your password. Use the verification code below on the password reset screen to set your new password:"
                + "    </p>"
                + "    <div style='text-align: center; margin: 30px 0;'>"
                + "      <div style='display: inline-block; background: #020617; border: 2px solid #38bdf8; border-radius: 14px; padding: 16px 36px; font-size: 32px; font-weight: 800; letter-spacing: 10px; color: #38bdf8; font-family: monospace; box-shadow: 0 0 20px rgba(56, 189, 248, 0.2);'>"
                + otp
                + "      </div>"
                + "    </div>"
                + "    <p style='color: #94a3b8; font-size: 13px; line-height: 1.6; margin-top: 24px;'>"
                + "      ⏱️ <strong>Valid for 10 minutes.</strong> Do not share this code with anyone.<br/>"
                + "      🔒 <em>If you did not make this request, you can safely ignore this email. Your current password remains active.</em>"
                + "    </p>"
                + "    <hr style='border: none; border-top: 1px solid #1e293b; margin: 28px 0;' />"
                + "    <p style='color: #64748b; font-size: 11px; text-align: center; margin: 0;'>"
                + "      © 2026 Vidhura AI • Understand Your Insurance Before It Understands You"
                + "    </p>"
                + "  </div>"
                + "</body>"
                + "</html>";
    }
}
