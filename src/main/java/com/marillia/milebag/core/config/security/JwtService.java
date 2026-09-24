package com.marillia.milebag.core.config.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emissao e validacao do JWT usado para autenticar requisicoes e propagar a
 * identidade do tenant (claim {@code tenantId}) - decisao de isolamento
 * multi-tenant documentada em {@code research.md} da feature 001, secao 3.
 *
 * <p>Este servico so cobre validacao/emissao do token em si. O fluxo de
 * login (verificacao de usuario/senha, HU22) fica em {@code AutenticacaoService}.</p>
 */
@Service
public class JwtService {

    private static final String CLAIM_TENANT_ID = "tenantId";
    private static final String CLAIM_PAPEL = "papel";

    private final JwtProperties properties;
    private final SecretKey chave;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.chave = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@code tenantId} nulo (HU14): token do administrador da plataforma,
     * que nao e escopado a nenhum tenant - a claim simplesmente nao e
     * incluida, em vez de gravar um UUID sentinela.
     */
    public String gerarToken(UUID userId, UUID tenantId, String papel) {
        Instant agora = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_PAPEL, papel)
                .issuedAt(Date.from(agora))
                .expiration(Date.from(agora.plus(properties.expirationMinutes(), ChronoUnit.MINUTES)));
        if (tenantId != null) {
            builder.claim(CLAIM_TENANT_ID, tenantId.toString());
        }
        return builder.signWith(chave).compact();
    }

    /**
     * @throws JwtException se o token estiver ausente, expirado, com
     *                       assinatura invalida ou sem as claims esperadas.
     */
    public AuthenticatedUser validarToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        String tenantIdClaim = claims.get(CLAIM_TENANT_ID, String.class);
        UUID tenantId = tenantIdClaim != null ? UUID.fromString(tenantIdClaim) : null;
        String papel = claims.get(CLAIM_PAPEL, String.class);
        return new AuthenticatedUser(userId, tenantId, papel);
    }
}
