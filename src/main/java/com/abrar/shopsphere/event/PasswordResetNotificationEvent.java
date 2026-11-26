
package com.abrar.shopsphere.event;

public record PasswordResetNotificationEvent(
        String email,
        String phoneNumber,
        String otp
) {
}
