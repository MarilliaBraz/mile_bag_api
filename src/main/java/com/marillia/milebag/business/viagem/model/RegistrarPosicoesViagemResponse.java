package com.marillia.milebag.business.viagem.model;

import java.util.List;

/**
 * Item 9: {@code pendentes} sao os pontos que ainda nao tem viagem no servidor (a viagem foi iniciada sem internet e
 * ainda nao sincronizou) - o app guarda e reenvia depois. Os demais entraram ou foram descartados (invalidos, repetidos).
 */
public record RegistrarPosicoesViagemResponse(int aceitas, List<String> pendentes) {
}
