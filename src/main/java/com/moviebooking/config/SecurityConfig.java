package com.moviebooking.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moviebooking.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * HTTP Basic auth with two roles (ADMIN, CUSTOMER).
 * Intentionally simple: no JWT/OAuth. Users live in the database (see user package).
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ObjectMapper json;

    public SecurityConfig(ObjectMapper json) {
        this.json = json;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .headers(h -> h.frameOptions(f -> f.sameOrigin())) // H2 console
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        // Public discovery endpoints
                        .requestMatchers(HttpMethod.GET,
                                "/api/cities/**", "/api/movies/**", "/api/theaters/**", "/api/shows/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .httpBasic(withDefaults())
                // Same JSON error shape as GlobalExceptionHandler for failures that happen in the filter chain
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                writeError(req, res, 401, "UNAUTHORIZED", "Authentication required"))
                        .accessDeniedHandler((req, res, ex) ->
                                writeError(req, res, 403, "FORBIDDEN", "You do not have permission to perform this action")));
        return http.build();
    }

    private void writeError(HttpServletRequest req, HttpServletResponse res, int status, String code, String message)
            throws IOException {
        res.setStatus(status);
        if (status == 401) res.setHeader("WWW-Authenticate", "Basic realm=\"movie-booking\"");
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), ApiError.of(status, code, message, req.getRequestURI()));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
