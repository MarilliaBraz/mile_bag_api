package com.marillia.milebag.business.bagagemencontrada.model;

import java.util.List;

/**
 * Descricao da bagagem sem etiqueta (HU35): tipo, tamanho, cor e aeroporto obrigatorios. Os valores chegam como texto
 * para que um valor fora das opcoes vire 400 com mensagem clara.
 */
public record DescreverBagagemRequest(
        String tipoVolume, String tamanho, String cor, List<String> sinais, String marca, String observacoes, String aeroportoCodigo) {
}
