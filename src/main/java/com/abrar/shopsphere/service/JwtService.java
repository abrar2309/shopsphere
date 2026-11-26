
package com.abrar.shopsphere.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Service
public class JwtService {

    private static final long EXPIRATION_TIME =
            1000L * 60 * 60;

    private final SecretKey secretKey;

    public JwtService(
            @Value("${shopsphere.jwt.secret}") String encodedSecret) {

        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret must be configured."
            );
        }

        final byte[] decodedSecret;

        try {
            decodedSecret = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "JWT secret must be a valid Base64 value.",
                    ex
            );
        }

        if (decodedSecret.length < 32) {
            throw new IllegalStateException(
                    "JWT HMAC secret must contain at least 256 bits."
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(decodedSecret);
    }

    public String generateToken(String email, String role) {

        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + EXPIRATION_TIME
        );

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {

        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {

        return extractAllClaims(token)
                .get("role", String.class);
    }

    private Claims extractAllClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isTokenValid(
            String token,
            String username) {

        try {

            Claims claims = extractAllClaims(token);

            String extractedUsername = claims.getSubject();

            Date expiration = claims.getExpiration();

            return extractedUsername != null
                    && extractedUsername.equals(username)
                    && expiration != null
                    && expiration.after(new Date());

        } catch (Exception ex) {

            return false;
        }
    }
}
