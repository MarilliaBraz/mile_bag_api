package com.marillia.milebag.business.dadosbdo.model;

import java.util.UUID;

/**
 * A empresa corrigiu os dados do passageiro de um BDO que ja estava com um motorista (V59) - o aviso por push sai
 * depois do commit ({@code NotificadorDadosBdo}).
 */
public record DadosBdoAlterados(UUID bdoId, String bdoNumero, UUID motoristaId, boolean enderecoMudou) {
}
