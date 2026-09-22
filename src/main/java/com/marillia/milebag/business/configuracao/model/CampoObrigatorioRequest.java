package com.marillia.milebag.business.configuracao.model;

/** {@code campo} chega como texto (nao o enum diretamente) para render um erro 400 limpo quando fora do catalogo, em vez de uma falha de desserializacao. */
public record CampoObrigatorioRequest(String campo, boolean obrigatorio) {
}
