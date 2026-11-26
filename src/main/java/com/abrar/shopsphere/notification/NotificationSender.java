
package com.abrar.shopsphere.notification;

import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.event.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import java.util.ArrayList;
import java.util.Properties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class NotificationSender {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationSender.class);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final NotificationProperties properties;
    private final String whatsappApiVersion;
    private final String smtpHost;

    public NotificationSender(
            ObjectProvider<JavaMailSender> mailSenders,
            NotificationProperties properties,
            @Value("${shopsphere.notifications.whatsapp-api-version:v22.0}")
            String whatsappApiVersion,
            @Value("${spring.mail.host:}") String smtpHost) {

        this.mailSenders = mailSenders;
        this.properties = properties;
        this.whatsappApiVersion = whatsappApiVersion;
        this.smtpHost = smtpHost;
    }

    // Existing method retained for compatibility.
    public void sendPasswordReset(User user, String otp) {
        sendPasswordReset(
                user.getEmail(),
                user.getPhoneNumber(),
                otp
        );
    }

    // Password reset: email mandatory, WhatsApp optional.
    public void sendPasswordReset(
            String email,
            String phoneNumber,
            String otp) {

        sendRequiredEmail(
                email,
                "ShopSphere password reset code",
                "Your ShopSphere password reset code is "
                        + otp
                        + ". It expires in 5 minutes. "
                        + "If you did not request this, ignore this email."
        );

        // WhatsApp failure must never block successful email delivery.
        try {
            sendWhatsAppTemplate(
                    phoneNumber,
                    properties.getOtpTemplate(),
                    List.of(otp)
            );
        } catch (Exception ex) {
            log.warn(
                    "WhatsApp password reset notification failed. recipient={}",
                    maskPhone(phoneNumber),
                    ex
            );
        }
    }

    // Existing order confirmation flow retained.
    public void sendOrderConfirmation(
            User user,
            OrderCreatedEvent event) {

        String orderId = String.valueOf(event.getOrderId());
        String amount = String.valueOf(event.getTotalAmount());
        String status = String.valueOf(event.getStatus());

        sendEmail(
                user.getEmail(),
                "ShopSphere order confirmation - #" + orderId,
                "Your order #" + orderId
                        + " has been created. Total: INR " + amount
                        + ". Status: " + status
                        + ". Thank you for shopping with ShopSphere."
        );

        sendWhatsAppTemplate(
                user.getPhoneNumber(),
                properties.getOrderTemplate(),
                List.of(orderId, amount, status)
        );
    }

    // Deliver email independently through Gmail and local MailHog.
    private void sendRequiredEmail(
            String to,
            String subject,
            String body) {

        if (to == null || to.isBlank()) {
            throw new IllegalStateException(
                    "Password reset email address is missing."
            );
        }

        deliverToBothMailboxes(to, subject, body, true);
    }

    // Order notifications use the same dual-delivery pipeline.
    private void sendEmail(
            String to,
            String subject,
            String body) {

        if (to == null || to.isBlank()) {
            return;
        }

        deliverToBothMailboxes(to, subject, body, false);
    }

    private void deliverToBothMailboxes(
            String to,
            String subject,
            String body,
            boolean required) {

        SimpleMailMessage message = new SimpleMailMessage();

        if (properties.getEmailFrom() != null
                && !properties.getEmailFrom().isBlank()) {
            message.setFrom(properties.getEmailFrom());
        }

        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        int successfulDeliveries = 0;
        List<Exception> failures = new ArrayList<>();

        // Gmail uses the existing Spring Boot SMTP configuration.
        JavaMailSender gmailSender = mailSenders.getIfAvailable();

        if (gmailSender != null) {
            try {
                gmailSender.send(message);
                successfulDeliveries++;

                log.info(
                        "Gmail SMTP delivery successful. recipient={}",
                        maskEmail(to)
                );
            } catch (Exception ex) {
                failures.add(ex);

                log.error(
                        "Gmail SMTP delivery failed. recipient={}",
                        maskEmail(to),
                        ex
                );
            }
        } else {
            failures.add(
                    new IllegalStateException("Gmail SMTP sender unavailable.")
            );
        }

        // MailHog is a separate local SMTP destination.
        try {
            JavaMailSenderImpl mailhog = new JavaMailSenderImpl();
            mailhog.setHost("host.docker.internal");
            mailhog.setPort(1025);
            mailhog.setProtocol("smtp");

            Properties mailProperties = new Properties();
            mailProperties.put("mail.smtp.auth", "false");
            mailProperties.put("mail.smtp.starttls.enable", "false");
            mailProperties.put("mail.smtp.connectiontimeout", "5000");
            mailProperties.put("mail.smtp.timeout", "5000");
            mailProperties.put("mail.smtp.writetimeout", "5000");

            mailhog.setJavaMailProperties(mailProperties);
            mailhog.send(message);

            successfulDeliveries++;

            log.info(
                    "MailHog delivery successful. recipient={}",
                    maskEmail(to)
            );

        } catch (Exception ex) {
            failures.add(ex);

            log.error(
                    "MailHog delivery failed. recipient={}",
                    maskEmail(to),
                    ex
            );
        }

        if (successfulDeliveries == 0) {
            throw new IllegalStateException(
                    "Email delivery failed through Gmail and MailHog.",
                    failures.isEmpty() ? null : failures.get(0)
            );
        }

        if (successfulDeliveries < 2) {
            log.warn(
                    "Email delivered to only one destination. recipient={}, successfulDestinations={}",
                    maskEmail(to),
                    successfulDeliveries
            );
        } else {
            log.info(
                    "Email delivered to Gmail and MailHog. recipient={}",
                    maskEmail(to)
            );
        }
    }
    private void sendWhatsAppTemplate(
            String phone,
            String template,
            List<String> values) {

        if (phone == null || phone.isBlank()) {
            return;
        }

        if (properties.getWhatsappToken() == null
                || properties.getWhatsappToken().isBlank()
                || properties.getWhatsappPhoneNumberId() == null
                || properties.getWhatsappPhoneNumberId().isBlank()) {

            log.warn(
                    "WhatsApp notification not sent: Meta Cloud API "
                            + "is not configured. recipient={}",
                    maskPhone(phone)
            );
            return;
        }

        List<Map<String, Object>> parameters = values.stream()
                .map(v -> Map.<String, Object>of(
                        "type", "text",
                        "text", v == null ? "" : v
                ))
                .toList();

        Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", phone.replaceAll("[^0-9]", ""),
                "type", "template",
                "template", Map.of(
                        "name", template,
                        "language", Map.of("code", "en_US"),
                        "components", List.of(
                                Map.of(
                                        "type", "body",
                                        "parameters", parameters
                                )
                        )
                )
        );

        try {
            RestClient.create(properties.getWhatsappGraphUrl())
                    .post()
                    .uri(
                            "/{version}/{phoneId}/messages",
                            whatsappApiVersion,
                            properties.getWhatsappPhoneNumberId()
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(
                            properties.getWhatsappToken()
                    ))
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

        } catch (Exception ex) {
            log.error(
                    "WhatsApp notification delivery failed for {}",
                    maskPhone(phone),
                    ex
            );
            throw ex;
        }
    }

    private String maskEmail(String value) {
        int at = value.indexOf('@');

        return at < 1
                ? "***"
                : value.charAt(0) + "***" + value.substring(at);
    }

    private String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }

        String digits = value.replaceAll("\\D", "");

        return digits.length() < 4
                ? "***"
                : "***" + digits.substring(digits.length() - 4);
    }
}
