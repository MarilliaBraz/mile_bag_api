package com.marillia.milebag.core.config.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.marillia.milebag.core.config.CorsProperties;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtService jwtService, CorsProperties corsProperties) {
        this.jwtService = jwtService;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // API stateless: sem cookie de sessao, sem formulario de login do
                // Spring Security - autenticacao e so por Bearer JWT.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api-docs/**", "/docs/**", "/docs").permitAll()
                        // HU09: "perfil exigido: administrador do tenant"
                        // (contracts/tarifas-api.md) - primeiro endpoint
                        // deste projeto restrito por perfil, nao so por
                        // autenticacao.
                        .requestMatchers("/api/v1/tarifas/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * O front-end (mile_bag_app) roda em outra origem da API mesmo em
     * desenvolvimento (Vite escolhe a proxima porta livre quando a padrao
     * ja esta ocupada) - sem esta configuracao, o preflight OPTIONS do
     * navegador e recusado pelo Spring Security antes de chegar a qualquer
     * controller, mesmo com um Bearer token valido.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuracao = new CorsConfiguration();
        configuracao.setAllowedOriginPatterns(corsProperties.allowedOriginPatterns());
        configuracao.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type"));

        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", configuracao);
        return fonte;
    }
}
