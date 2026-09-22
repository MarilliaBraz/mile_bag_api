package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Parametrizacao do convite de administrador (HU15) - prazo de validade e a
 * base de URL usada no link de ativacao enviado por e-mail. Assumptions do
 * spec desta HU delega o valor exato do prazo para a implementacao (nao
 * fixado em requisito algum).
 */
@ConfigurationProperties(prefix = "milebag.convite")
public record ConviteProperties(long prazoValidadeHoras, String urlBase) {
}
