package com.abrar.shopsphere.security;

import com.abrar.shopsphere.service.CustomUserDetailsService;
import com.abrar.shopsphere.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter
        extends OncePerRequestFilter {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    JwtAuthenticationFilter.class
            );

    private final JwtService jwtService;

    private final CustomUserDetailsService userDetailsService;


    // ============================================================
    // CONSTRUCTOR
    // ============================================================

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService) {

        this.jwtService = jwtService;

        this.userDetailsService =
                userDetailsService;
    }


    // ============================================================
    // ENDPOINTS THAT DON'T REQUIRE JWT FILTER
    // ============================================================

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        String path =
                request.getServletPath();

        return path.equals("/auth/login")
                || path.equals("/auth/register");
    }


    // ============================================================
    // JWT FILTER
    // ============================================================

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {


        // --------------------------------------------------------
        // 1. GET AUTHORIZATION HEADER
        // --------------------------------------------------------

        final String authHeader =
                request.getHeader("Authorization");


        String username = null;

        String jwt = null;


        // --------------------------------------------------------
        // 2. CHECK FOR BEARER TOKEN
        // --------------------------------------------------------

        if (authHeader != null
                && authHeader.startsWith("Bearer ")) {

            /*
             * Remove:
             *
             * Bearer
             *
             * and the following space.
             *
             * What remains is the actual JWT.
             */

            jwt = authHeader.substring(7);


            try {

                // ------------------------------------------------
                // 3. EXTRACT EMAIL FROM JWT
                // ------------------------------------------------

                username =
                        jwtService.extractUsername(jwt);

                logger.debug(
                        "JWT username extracted: {}",
                        username
                );

            } catch (Exception e) {

                /*
                 * IMPORTANT:
                 *
                 * Previously we silently ignored this exception.
                 *
                 * Now we log it so we can actually diagnose
                 * invalid/expired/malformed JWTs.
                 */

                logger.error(
                        "JWT extraction failed",
                        e
                );

                username = null;
            }
        }


        // --------------------------------------------------------
        // 4. AUTHENTICATE USER
        // --------------------------------------------------------

        if (username != null
                && SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {

            try {

                // ------------------------------------------------
                // 5. LOAD USER FROM DATABASE
                // ------------------------------------------------

                UserDetails userDetails =
                        userDetailsService
                                .loadUserByUsername(username);


                // ------------------------------------------------
                // 6. VALIDATE JWT
                // ------------------------------------------------

                if (jwtService.isTokenValid(
                        jwt,
                        userDetails.getUsername())) {

                    logger.info(
                            "JWT validated successfully for {}",
                            username
                    );


                    // --------------------------------------------
                    // 7. CREATE AUTHENTICATION OBJECT
                    // --------------------------------------------

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );


                    // --------------------------------------------
                    // 8. ADD REQUEST DETAILS
                    // --------------------------------------------

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );


                    // --------------------------------------------
                    // 9. STORE AUTHENTICATION
                    // --------------------------------------------

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(
                                    authentication
                            );


                    logger.info(
                            "Authentication stored in SecurityContext for {} with authorities {}",
                            username,
                            userDetails.getAuthorities()
                    );

                } else {

                    logger.warn(
                            "JWT validation failed for {}",
                            username
                    );
                }

            } catch (Exception e) {

                /*
                 * This catches problems such as:
                 *
                 * - User not found
                 * - Database lookup problem
                 * - Unexpected authentication error
                 */

                logger.error(
                        "Failed to authenticate JWT user: {}",
                        username,
                        e
                );
            }
        }


        // --------------------------------------------------------
        // 10. CONTINUE FILTER CHAIN
        // --------------------------------------------------------

        filterChain.doFilter(
                request,
                response
        );
    }
}