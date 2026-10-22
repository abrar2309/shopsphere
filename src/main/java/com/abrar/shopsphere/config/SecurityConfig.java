
package com.abrar.shopsphere.config;

import com.abrar.shopsphere.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /*
     * CORS configuration for the local React frontend.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://localhost:5175",
                "http://localhost:5176",
                "http://localhost:5177",
                "http://localhost:5178",
                "http://localhost:8082"


        ));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "Accept"
        ));

        configuration.setExposedHeaders(List.of(
                "X-RateLimit-Limit",
                "X-RateLimit-Remaining"
        ));

        // The frontend uses bearer-token authentication, not cookies.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                // Stateless REST APIs use JWT authentication.
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        // Allow Spring Security to process error dispatches.
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        // Public health endpoints.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // Public registration and login.
                        .requestMatchers(
                                "/auth/**"
                        ).permitAll()

                        // Public Swagger documentation.
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Admin-only endpoints.
                        .requestMatchers(
                                "/admin/**"
                        ).hasRole("ADMIN")

                        // Product creation: sellers and admins.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/products",
                                "/products/**"
                        ).hasAnyRole("SELLER", "ADMIN")

                        // Product updates: sellers and admins.
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/products",
                                "/products/**"
                        ).hasAnyRole("SELLER", "ADMIN")

                        // Product deletion: sellers and admins.
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/products",
                                "/products/**"
                        ).hasAnyRole("SELLER", "ADMIN")

                        // Public product browsing.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/products",
                                "/products/**"
                        ).permitAll()

                        // Public category browsing.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/categories",
                                "/categories/**"
                        ).permitAll()

                        // Category creation: admins only.
                        .requestMatchers(
                                HttpMethod.POST,
                                "/categories",
                                "/categories/**"
                        ).hasRole("ADMIN")

                        // Category updates: admins only.
                        .requestMatchers(
                                HttpMethod.PUT,
                                "/categories",
                                "/categories/**"
                        ).hasRole("ADMIN")

                        // Category partial updates: admins only.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/categories",
                                "/categories/**"
                        ).hasRole("ADMIN")

                        // Category deletion: admins only.
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/categories",
                                "/categories/**"
                        ).hasRole("ADMIN")

                        // All other requests require authentication.
                        .anyRequest().authenticated()
                )

                // Preserve JWT authentication.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
