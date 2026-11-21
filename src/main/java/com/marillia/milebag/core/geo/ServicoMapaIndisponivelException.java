package com.marillia.milebag.core.geo;

/** O servico de mapa (Nominatim) nao respondeu ou recusou o pedido - diferente de "nao achou o endereco". */
public class ServicoMapaIndisponivelException extends RuntimeException {

    public ServicoMapaIndisponivelException(Throwable causa) {
        super("Servico de mapa indisponivel.", causa);
    }
}
