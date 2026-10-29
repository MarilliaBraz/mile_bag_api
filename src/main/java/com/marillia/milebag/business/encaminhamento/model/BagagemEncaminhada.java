package com.marillia.milebag.business.encaminhamento.model;

import java.util.UUID;

/**
 * Evento de uma bagagem encaminhada (HU37): o aviso ao passageiro escuta depois do commit. Leva so o necessario para o
 * e-mail - o endereco do passageiro nao e guardado fora do BDO da empresa.
 */
public record BagagemEncaminhada(UUID encaminhamentoId, String email, String passageiroNome, String companhia, String empresa, String identificacao) {
}
