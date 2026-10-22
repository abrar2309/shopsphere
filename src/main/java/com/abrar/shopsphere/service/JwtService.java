package com.abrar.shopsphere.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final Logger logger =
            LoggerFactory.getLogger(JwtService.class);


    // ============================================================
    // SECRET KEY
    // ============================================================

    /*
     * Secret key used to sign and verify JWTs.
     *
     * IMPORTANT:
     * In production, this should NOT be hardcoded.
     * We will move it to environment/configuration
     * during the production security review.
     */
    private static final String SECRET_KEY =
            "ShopSphereSuperSecretKeyForJWTGeneration123456789";


    // ============================================================
    // JWT EXPIRATION
    // ============================================================

    /*
     * Token expiration:
     *
     * 1000 milliseconds
     * × 60 seconds
     * × 60 minutes
     *
     * = 1 hour
     */
    private static final long EXPIRATION_TIME =
            1000 * 60 * 60;


    private final SecretKey secretKey;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public JwtService() {

        this.secretKey =
                Keys.hmacShaKeyFor(
                        SECRET_KEY.getBytes(StandardCharsets.UTF_8)
                );
    }


    // ============================================================
    // GENERATE JWT
    // ============================================================

    public String generateToken(
            String email,
            String role) {

        Date now =
                new Date();

        Date expiration =
                new Date(
                        now.getTime()
                                + EXPIRATION_TIME
                );


        return Jwts.builder()

                // ----------------------------------------------
                // SUBJECT
                // ----------------------------------------------

                .subject(email)

                // ----------------------------------------------
                // ROLE CLAIM
                // ----------------------------------------------

                .claim("role", role)

                // ----------------------------------------------
                // ISSUED AT
                // ----------------------------------------------

                .issuedAt(now)

                // ----------------------------------------------
                // EXPIRATION
                // ----------------------------------------------

                .expiration(expiration)

                // ----------------------------------------------
                // SIGN JWT
                // ----------------------------------------------

                .signWith(secretKey)

                .compact();
    }


    // ============================================================
    // EXTRACT USERNAME / EMAIL
    // ============================================================

    public String extractUsername(
            String token) {

        return extractAllClaims(token)
                .getSubject();
    }


    // ============================================================
    // EXTRACT ROLE
    // ============================================================

    public String extractRole(
            String token) {

        return extractAllClaims(token)
                .get("role", String.class);
    }


    // ============================================================
    // EXTRACT ALL CLAIMS
    // ============================================================

    private Claims extractAllClaims(
            String token) {

        return Jwts.parser()

                .verifyWith(secretKey)

                .build()

                .parseSignedClaims(token)

                .getPayload();
    }


    // ============================================================
    // VALIDATE TOKEN
    // ============================================================

    public boolean isTokenValid(
            String token,
            String username) {

        try {

            String extractedUsername =
                    extractUsername(token);


            boolean usernameMatches =
                    extractedUsername.equals(username);


            boolean tokenExpired =
                    isTokenExpired(token);


            boolean valid =
                    usernameMatches
                            && !tokenExpired;


            if (!usernameMatches) {

                logger.warn(
                        "JWT username mismatch. Token user: {}, expected user: {}",
                        extractedUsername,
                        username
                );
            }


            if (tokenExpired) {

                logger.warn(
                        "JWT token has expired for user: {}",
                        username
                );
            }


            return valid;

        } catch (Exception e) {

            logger.error(
                    "JWT validation failed",
                    e
            );

            return false;
        }
    }


    // ============================================================
    // CHECK TOKEN EXPIRATION
    // ============================================================

    private boolean isTokenExpired(
            String token) {

        Date expiration =
                extractAllClaims(token)
                        .getExpiration();

        return expiration.before(
                new Date()
        );
    }
}