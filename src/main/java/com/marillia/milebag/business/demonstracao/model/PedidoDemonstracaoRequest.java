package com.marillia.milebag.business.demonstracao.model;

/**
 * Pedido de demonstracao da pagina institucional (HU38, contracts/demonstracao-api.md). {@code site} e o campo-isca
 * (invisivel para pessoas); {@code preenchidoEmMs} e o tempo entre abrir a pagina e enviar.
 */
public record PedidoDemonstracaoRequest(String nome, String empresa, PerfilCliente perfil, String email, String telefone, String mensagem,
        Boolean consentimento, String site, Long preenchidoEmMs) {
}
