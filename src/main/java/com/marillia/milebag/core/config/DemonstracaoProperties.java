package com.marillia.milebag.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Para onde vai o pedido de demonstracao da pagina institucional (HU38). O endereco nao aparece na pagina. */
@ConfigurationProperties(prefix = "milebag.demonstracao")
public record DemonstracaoProperties(String destino) {
}
