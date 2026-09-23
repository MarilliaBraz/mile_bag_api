package com.marillia.milebag.business.viagem.service;

import java.util.Optional;
import java.util.UUID;

import com.marillia.milebag.core.geo.RotaAlternativa;
import com.marillia.milebag.core.geo.TipoSelecao;

/**
 * Porta (Dependency Inversion, CONVENTIONS.md 2.1) pela qual {@code
 * ViagemService} le a rota selecionada para uma entrega (HU03), sem
 * depender diretamente do pacote {@code business.rota} nem de {@code
 * PropostaRotaRepository}. Implementada por {@code RotaService} - evita a
 * dependencia circular que existiria se {@code viagem} importasse {@code
 * rota} enquanto {@code rota} ja depende de {@code viagem} para {@link
 * #buscarSelecionada} nao, mas para {@code registrarCalculoRotaSeTrajetoExistir}.
 */
public interface SelecaoRotaProvider {

    Optional<RotaSelecionada> buscarSelecionada(UUID entregaId);

    record RotaSelecionada(TipoSelecao tipoSelecao, RotaAlternativa rotaEscolhida) {
    }
}
