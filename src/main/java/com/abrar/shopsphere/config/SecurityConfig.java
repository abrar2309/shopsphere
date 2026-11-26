
package com.abrar.shopsphere.config;

import com.abrar.shopsphere.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /*
     * CORS configuration for the React frontend.
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

                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174",
                "http://127.0.0.1:5175",
                "http://127.0.0.1:5176",
                "http://127.0.0.1:5177",
                "http://127.0.0.1:5178",

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

        // JWT bearer authentication does not require cookies.
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .cors(cors ->
                        cors.configurationSource(corsConfigurationSource())
                )

                // Stateless REST API using JWT.
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                // Return 401 for unauthenticated requests.
                .exceptionHandling(exception ->
                        exception.authenticationEntryPoint(
                                new HttpStatusEntryPoint(
                                        HttpStatus.UNAUTHORIZED
                                )
                        )
                )

                .authorizeHttpRequests(auth -> auth

                        // Allow error dispatches.
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        // Public health endpoints.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // Public authentication endpoints.
                        .requestMatchers(
                                "/auth/**"
                        ).permitAll()

                        // Public API documentation.
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll()

                        // Public access to uploaded product images.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/uploads/**"
                        ).permitAll()

                        // Admin-only endpoints.
                        .requestMatchers(
                                "/admin/**"
                        ).hasRole("ADMIN")

                        // Current user's own profile.
                        .requestMatchers(
                                "/users/me",
                                "/users/me/**"
                        ).authenticated()

                        // Other user-management endpoints.
                        .requestMatchers(
                                "/users",
                                "/users/**"
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

                        // Product partial updates: sellers and admins.
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/products",
                                "/products/**"
                        ).hasAnyRole("SELLER", "ADMIN")

                        // Product deletion: sellers and admins.
                        .requestMatchers(
                                HttpMethod.DELETE,
                                "/products",
                                "/products/**"
                        ).hasAnyRole("SELLER", "ADMIN")
                        // Public read-only AI semantic product search.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/ai/products/search",
                                "/ai/bge/products/search"
                        ).permitAll()
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

                        // All remaining endpoints require authentication.
                        .anyRequest().authenticated()
                )

                // Register JWT authentication filter.
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}
