package com.abrar.shopsphere.notification;

import com.abrar.shopsphere.event.PasswordResetNotificationEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PasswordResetNotificationListener {

    private final NotificationSender notifications;

    public PasswordResetNotificationListener(NotificationSender notifications) {
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePasswordReset(PasswordResetNotificationEvent event) {
        notifications.sendPasswordReset(
                event.email(),
                event.phoneNumber(),
                event.otp()
        );
    }
}