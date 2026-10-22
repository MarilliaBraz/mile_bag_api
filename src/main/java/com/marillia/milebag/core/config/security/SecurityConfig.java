package com.marillia.milebag.core.config.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final SessaoValidador sessaoValidador;

    // @Lazy: o validador depende de repositorios JPA, e este @Configuration tambem define beans (PasswordEncoder) que outros servicos usam.
    public SecurityConfig(JwtService jwtService, CorsProperties corsProperties, @Lazy SessaoValidador sessaoValidador) {
        this.sessaoValidador = sessaoValidador;
        this.jwtService = jwtService;
        this.corsProperties = corsProperties;
    }

    /** Hash da senha definida na ativacao do convite do administrador (HU15) - nunca armazenada em claro. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
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
                        // HU15: tela de ativacao do convite roda antes de
                        // qualquer login (contracts/convite-api.md) - sem
                        // JWT a apresentar, entao publico por definicao.
                        .requestMatchers("/api/v1/convites/**").permitAll()
                        // HU22: o login emite o JWT - nao ha token a apresentar.
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/esqueci-senha", "/api/v1/auth/redefinir-senha").permitAll()
                        // HU18: webhook chamado pelos servidores do Telegram,
                        // nao pelo front-end - autenticado pelo cabecalho
                        // X-Telegram-Bot-Api-Secret-Token (TelegramWebhookController),
                        // nao por JWT de usuario.
                        .requestMatchers("/api/v1/webhooks/telegram").permitAll()
                        // HU09: "perfil exigido: administrador do tenant"
                        // (contracts/tarifas-api.md) - primeiro endpoint
                        // deste projeto restrito por perfil, nao so por
                        // autenticacao.
                        .requestMatchers("/api/v1/tarifas/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU23: cadastro de motoristas so pelo administrador do tenant.
                        .requestMatchers("/api/v1/motoristas/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU10: leitura tambem liberada ao motorista (para
                        // exibir pedagios na escolha de rota, HU03), mas
                        // escrita (POST) continua restrita ao administrador
                        // do tenant - por isso o matcher de GET vem antes do
                        // matcher geral do mesmo caminho.
                        .requestMatchers(HttpMethod.GET, "/api/v1/pracas-pedagio/**")
                        .hasAnyRole(Papeis.ADMINISTRADOR_TENANT, Papeis.MOTORISTA)
                        .requestMatchers("/api/v1/pracas-pedagio/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU11: das 5 categorias de configuracao, so
                        // motivos de ocorrencia e campos de baixa tem
                        // leitura liberada ao motorista - as outras 3
                        // (bases, companhias, periodicidade) ficam restritas
                        // ao administrador do tenant mesmo para GET.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/configuracoes/motivos-ocorrencia",
                                "/api/v1/configuracoes/campos-baixa")
                        .hasAnyRole(Papeis.ADMINISTRADOR_TENANT, Papeis.MOTORISTA)
                        .requestMatchers("/api/v1/configuracoes/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU12: "perfil exigido: administrador do tenant"
                        // sem excecao de leitura para o motorista.
                        .requestMatchers("/api/v1/fechamentos/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU13: "restritos ao perfil de administrador do
                        // proprio tenant" (contracts/identidade-visual-api.md)
                        // - inclui o endpoint de leitura do logotipo, que
                        // nao tem contrato proprio mas segue a mesma regra.
                        .requestMatchers("/api/v1/identidade-visual/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU14: "restritos ao perfil de administrador da
                        // plataforma (provedor)" (contracts/tenants-api.md)
                        // - perfil distinto de ADMINISTRADOR_TENANT, cujo
                        // token nao carrega claim de tenant (Tenant.java).
                        .requestMatchers("/api/v1/plataforma/**").hasRole(Papeis.ADMINISTRADOR_PLATAFORMA)
                        // HU16: "consulta de plano vigente tambem acessivel
                        // ao administrador do proprio tenant" - unico
                        // endpoint desta HU fora de /plataforma/**, ja que os
                        // demais (CRUD de plano, troca de plano) sao
                        // exclusivos do administrador da plataforma.
                        .requestMatchers("/api/v1/plano").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        // HU30: o motorista aciona, consulta o proprio alerta aberto
                        // e cancela o que acionou por engano; listar e atender
                        // sao do administrador do tenant.
                        .requestMatchers(HttpMethod.POST, "/api/v1/emergencias", "/api/v1/emergencias/*/cancelar")
                        .hasRole(Papeis.MOTORISTA)
                        .requestMatchers(HttpMethod.GET, "/api/v1/emergencias/minha").hasRole(Papeis.MOTORISTA)
                        .requestMatchers("/api/v1/emergencias/**").hasRole(Papeis.ADMINISTRADOR_TENANT)
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, sessaoValidador), UsernamePasswordAuthenticationFilter.class)
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
