package com.vidhura.ai.config;

import com.vidhura.ai.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Value("${cors.allowed-origins:*}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .xssProtection(xss -> xss.headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31536000).includeSubDomains(true))
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/google", "/api/auth/refresh",
                                "/api/auth/forgot-password", "/api/auth/verify-otp", "/api/auth/reset-password").permitAll()
                        // Public / client read endpoints for catalog policies and statistics
                        .requestMatchers(org.springframework.http.HttpMethod.GET,
                                "/api/policies", "/api/policies/catalog/**", "/api/catalog/**", "/api/catalog", "/api/policies/catalog/stats", "/api/catalog/stats").permitAll()
                        // Strict Role-Based Access Control: Mutating governance actions strictly require ROLE_ADMIN or ROLE_REVIEWER
                        .requestMatchers("/api/policies/discover", "/api/catalog/discover").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers("/api/policies/*/verify", "/api/catalog/*/verify", "/api/policies/verify/*", "/api/catalog/verify/*").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers("/api/policies/*/flag", "/api/catalog/*/flag", "/api/policies/flag/*", "/api/catalog/flag/*").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers("/api/policies/*/rollback", "/api/catalog/*/rollback", "/api/policies/rollback/*", "/api/catalog/rollback/*").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers("/api/policies/cache/refresh", "/api/catalog/cache/refresh", "/api/policies/refresh", "/api/catalog/refresh").hasAnyRole("ADMIN", "REVIEWER")
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/policies/sources/**", "/api/catalog/sources/**", "/api/policies/crawl-all", "/api/catalog/crawl-all").hasAnyRole("ADMIN", "REVIEWER")

                        // Read-Only inspection and benchmark evaluation endpoints: Allowed for Reviewers, Admins, and Audit Guests
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/policies/drafts", "/api/catalog/drafts").hasAnyRole("ADMIN", "REVIEWER", "GUEST")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/policies/sources", "/api/catalog/sources").hasAnyRole("ADMIN", "REVIEWER", "GUEST")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/policies/documents", "/api/catalog/documents").hasAnyRole("ADMIN", "REVIEWER", "GUEST")
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/policies/quality-report", "/api/catalog/quality-report", "/api/policies/benchmark/**", "/api/catalog/benchmark/**").hasAnyRole("ADMIN", "REVIEWER", "GUEST")
                        .anyRequest().permitAll()
                );

        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if ("*".equals(allowedOrigins)) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOriginPatterns(
                    Arrays.asList(allowedOrigins.split(","))
            );
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
