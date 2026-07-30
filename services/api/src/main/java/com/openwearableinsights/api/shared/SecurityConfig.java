package com.openwearableinsights.api.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for local-first operation.
 *
 * <p>Phase 1: single-user local mode, enforced by a static shared-secret
 * token (see {@link LocalApiTokenAuthFilter} and {@link LocalApiTokenStore},
 * ADR-0008) rather than Spring Security's {@code UserDetailsService}/Basic-Auth
 * machinery — this is a single static token check, not multi-user auth.
 * Phase 6 will evolve this to OIDC for multi-user hosted deployment.
 *
 * <p>CORS is configured to allow the Flutter web client on localhost.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final LocalApiTokenAuthFilter localApiTokenAuthFilter;

    public SecurityConfig(LocalApiTokenAuthFilter localApiTokenAuthFilter) {
        this.localApiTokenAuthFilter = localApiTokenAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API-only, no browser forms
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .authorizeHttpRequests(auth -> auth
                // Authorization is enforced by LocalApiTokenAuthFilter below;
                // Spring Security's own authorization is a pass-through here.
                .anyRequest().permitAll()
            )
            .addFilterBefore(localApiTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of(
            "http://localhost:*",
            "http://127.0.0.1:*"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}