package com.abrar.shopsphere.service;

import com.abrar.shopsphere.entity.User;
import com.abrar.shopsphere.event.PasswordResetNotificationEvent;
import com.abrar.shopsphere.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(
            JdbcTemplate jdbc,
            UserRepository users,
            PasswordEncoder encoder,
            ApplicationEventPublisher events
    ) {
        this.jdbc = jdbc;
        this.users = users;
        this.encoder = encoder;
        this.events = events;
    }

    @Transactional
    public void request(String email) {

        if (email == null || email.isBlank()) {
            return;
        }

        Optional<User> found =
                users.findByEmail(email.trim().toLowerCase());

        if (found.isEmpty()) {
            return;
        }

        User user = users.findByIdForUpdate(found.get().getId())
                .orElse(null);

        if (user == null) {
            return;
        }

        Integer recent = jdbc.queryForObject(
                """
                SELECT COUNT(*)
                FROM password_reset_otps
                WHERE user_id = ?
                  AND created_at > ?
                """,
                Integer.class,
                user.getId(),
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(60))
        );

        if (recent != null && recent > 0) {
            return;
        }

        jdbc.update(
                """
                UPDATE password_reset_otps
                SET used_at = NOW()
                WHERE user_id = ?
                  AND used_at IS NULL
                """,
                user.getId()
        );

        String otp = String.format("%06d", random.nextInt(1_000_000));

        jdbc.update(
                """
                INSERT INTO password_reset_otps
                (user_id, otp_hash, expires_at, attempts, created_at)
                VALUES (?, ?, ?, 0, NOW())
                """,
                user.getId(),
                encoder.encode(otp),
                Timestamp.valueOf(LocalDateTime.now().plusMinutes(5))
        );

        events.publishEvent(
                new PasswordResetNotificationEvent(
                        user.getEmail(),
                        user.getPhoneNumber(),
                        otp
                )
        );
    }

    @Transactional
    public boolean confirm(
            String email,
            String otp,
            String newPassword
    ) {

        if (email == null || otp == null
                || !otp.matches("\\d{6}")
                || newPassword == null
                || newPassword.length() < 10) {
            return false;
        }

        Optional<User> found =
                users.findByEmail(email.trim().toLowerCase());

        if (found.isEmpty()) {
            return false;
        }

        User user = users.findByIdForUpdate(found.get().getId())
                .orElse(null);

        if (user == null) {
            return false;
        }

        var rows = jdbc.query(
                """
                SELECT id, otp_hash, expires_at, attempts
                FROM password_reset_otps
                WHERE user_id = ?
                  AND used_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                FOR UPDATE
                """,
                (rs, rowNum) -> new ResetRow(
                        rs.getLong("id"),
                        rs.getString("otp_hash"),
                        rs.getTimestamp("expires_at").toLocalDateTime(),
                        rs.getInt("attempts")
                ),
                user.getId()
        );

        if (rows.isEmpty()) {
            return false;
        }

        ResetRow row = rows.get(0);

        if (row.attempts() >= 5
                || row.expiresAt().isBefore(LocalDateTime.now())) {
            return false;
        }

        if (!encoder.matches(otp, row.hash())) {
            jdbc.update(
                    """
                    UPDATE password_reset_otps
                    SET attempts = attempts + 1
                    WHERE id = ?
                    """,
                    row.id()
            );

            // Return normally so the failed-attempt increment commits.
            return false;
        }

        user.setPassword(encoder.encode(newPassword));
        users.save(user);

        jdbc.update(
                """
                UPDATE password_reset_otps
                SET used_at = NOW()
                WHERE id = ?
                """,
                row.id()
        );

        return true;
    }

    private record ResetRow(
            Long id,
            String hash,
            LocalDateTime expiresAt,
            int attempts
    ) {
    }
}