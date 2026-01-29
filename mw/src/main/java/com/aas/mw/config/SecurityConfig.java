package com.aas.mw.config;

import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.JwtService;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            ErpSessionStore erpSessionStore) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtService, erpSessionStore);
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/login",
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, "/api/setup/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/vendors").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/shops").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/categories").hasAnyRole("ADMIN", "HELPER")
                .requestMatchers(HttpMethod.POST, "/api/items").hasAnyRole("ADMIN", "HELPER")
                .requestMatchers(HttpMethod.POST, "/api/orders").hasAnyRole("ADMIN", "SHOP")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/assign-vendor").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/status").hasAnyRole("ADMIN", "VENDOR", "HELPER")
                .requestMatchers(HttpMethod.POST, "/api/invoices").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/payments").hasAnyRole("ADMIN", "SHOP")
                .requestMatchers(HttpMethod.GET, "/api/orders").hasAnyRole("ADMIN", "VENDOR", "SHOP", "HELPER")
                .requestMatchers(HttpMethod.GET, "/api/orders/export").hasAnyRole("ADMIN", "VENDOR", "SHOP", "HELPER")
                .requestMatchers(HttpMethod.GET, "/api/invoices/**").hasAnyRole("ADMIN", "SHOP")
                .requestMatchers(HttpMethod.GET, "/api/invoices/export").hasAnyRole("ADMIN", "SHOP")
                .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "VENDOR", "SHOP")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origin-patterns:*}") String allowedOriginPatterns) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
