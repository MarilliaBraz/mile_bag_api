package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Diretorio base do volume em disco onde arquivos de evidencia (ex: imagem
 * original do BDO, FR-006) sao gravados, sempre sob um subdiretorio com
 * prefixo do tenant - nunca hardcoded, ver {@code CONVENTIONS.md} secao 2.3.
 */
@ConfigurationProperties(prefix = "milebag.armazenamento")
public record ArmazenamentoProperties(String diretorioBase) {
}
