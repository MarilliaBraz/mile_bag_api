package com.marillia.milebag.core.config.security;

import java.io.IOException;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Resolve o tenant e o usuario autenticado uma unica vez por requisicao, a
 * partir do header {@code Authorization: Bearer <token>}, e propaga por
 * {@link TenantContext} (nunca aceito como parametro vindo do cliente).
 *
 * <p>{@link TenantContext#clear()} roda sempre no {@code finally}: threads de
 * um pool de conexoes sao reaproveitadas entre requisicoes, entao um valor
 * esquecido aqui vazaria o tenant de uma requisicao anterior para a
 * seguinte - a mesma garantia de isolamento que a politica de Row Level
 * Security exige do lado do banco (RNF01).</p>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SessaoValidador sessaoValidador;

    public JwtAuthenticationFilter(JwtService jwtService, SessaoValidador sessaoValidador) {
        this.jwtService = jwtService;
        this.sessaoValidador = sessaoValidador;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String header = request.getHeader(AUTH_HEADER);
            if (header != null && header.startsWith(BEARER_PREFIX)) {
                String token = header.substring(BEARER_PREFIX.length());
                AuthenticatedUser usuario = jwtService.validarToken(token);

                TenantContext.set(usuario.tenantId());

                // Token com assinatura valida mas de sessao revogada (conta desativada, senha trocada): segue sem autenticacao.
                if (!sessaoValidador.sessaoValida(usuario)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.papel()));
                var authentication = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } catch (JwtException ex) {
            // Token ausente/invalido/expirado: segue sem autenticacao: o
            // Spring Security responde 401 nos endpoints protegidos
            // (SecurityConfig) - nunca lancar 500 por causa de um token ruim.
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
