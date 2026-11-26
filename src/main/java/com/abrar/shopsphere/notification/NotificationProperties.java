package com.abrar.shopsphere.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopsphere.notifications")
public class NotificationProperties {

    private String emailFrom = "";
    private String whatsappGraphUrl = "https://graph.facebook.com";
    private String whatsappPhoneNumberId = "";
    private String whatsappToken = "";
    private String otpTemplate = "shopsphere_password_reset";
    private String orderTemplate = "shopsphere_order_confirmation";

    public String getEmailFrom() {
        return emailFrom;
    }

    public void setEmailFrom(String v) {
        emailFrom = v;
    }

    public String getWhatsappGraphUrl() {
        return whatsappGraphUrl;
    }

    public void setWhatsappGraphUrl(String v) {
        whatsappGraphUrl = v;
    }

    public String getWhatsappPhoneNumberId() {
        return whatsappPhoneNumberId;
    }

    public void setWhatsappPhoneNumberId(String v) {
        whatsappPhoneNumberId = v;
    }

    public String getWhatsappToken() {
        return whatsappToken;
    }

    public void setWhatsappToken(String v) {
        whatsappToken = v;
    }

    public String getOtpTemplate() {
        return otpTemplate;
    }

    public void setOtpTemplate(String v) {
        otpTemplate = v;
    }

    public String getOrderTemplate() {
        return orderTemplate;
    }

    public void setOrderTemplate(String v) {
        orderTemplate = v;
    }
}