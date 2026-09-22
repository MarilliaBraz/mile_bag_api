package com.marillia.milebag.business.tenant.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * Token opaco e nao previsivel para o convite de administrador (HU15,
 * research.md: "UUID v4 criptograficamente aleatorio ou equivalente").
 * {@code UUID.randomUUID()} ja usa {@link java.security.SecureRandom}
 * internamente - componente dedicado so para isolar essa decisao caso um
 * gerador diferente seja necessario no futuro (tasks.md T006).
 */
@Component
public class TokenConviteGenerator {

    public UUID gerar() {
        return UUID.randomUUID();
    }
}
