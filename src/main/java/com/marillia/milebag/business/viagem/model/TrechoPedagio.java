package com.marillia.milebag.business.viagem.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sinalizacao de pedagio dentro de um {@code CalculoRota} (FR-010).
 *
 * <p>Dois formatos convivem: (1) por praca (HU24) - {@code pracaId} e {@code valorUnitario} (por passagem, congelado
 * quando o calculo foi registrado) preenchidos, {@code valorCadastrado = true}; {@code valorUnitario} nulo com
 * {@code valorCadastrado = false} e uma praca detectada sem nenhum valor vigente na data; (2) generico - so o
 * indicador agregado do motor de rotas (Valhalla), sem praca nem valor: o traçado tem pedagio mas nenhuma praca
 * cadastrada foi encontrada nele, entao nao ha como calcular o reembolso (o item de fechamento fica pendente).</p>
 */
public record TrechoPedagio(String descricao, boolean valorCadastrado, UUID pracaId, BigDecimal valorUnitario) {

    public TrechoPedagio(String descricao, boolean valorCadastrado) {
        this(descricao, valorCadastrado, null, null);
    }
}
