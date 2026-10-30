package com.marillia.milebag.business.bagagemencontrada.service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;

import com.marillia.milebag.business.bagagemencontrada.model.CorBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.OrigemLocalizacao;
import com.marillia.milebag.business.bagagemencontrada.model.SinalParticular;
import com.marillia.milebag.business.bagagemencontrada.model.SituacaoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TamanhoBagagem;
import com.marillia.milebag.business.bagagemencontrada.model.TipoBagagemEncontrada;
import com.marillia.milebag.business.bagagemencontrada.model.TipoVolume;

/** Os valores da bagagem encontrada por extenso, em portugues, para a ficha e a planilha (HU36). Os mesmos textos do app. */
final class RotulosBagagem {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(BagagemEncontradaService.BRASILIA);

    private RotulosBagagem() {
    }

    static String tipo(TipoBagagemEncontrada tipo) {
        return tipo == TipoBagagemEncontrada.COM_ETIQUETA ? "Com etiqueta" : "Sem etiqueta";
    }

    static String situacao(SituacaoBagagemEncontrada situacao) {
        return situacao == SituacaoBagagemEncontrada.REGISTRADA ? "Registrada" : "Rascunho";
    }

    /** Etapa da entrega da bagagem encaminhada (HU37), que chega como texto do dominio {@code encaminhamento}. */
    static String etapa(String etapa) {
        return etapa == null ? "" : switch (etapa) {
            case "AGUARDANDO_MOTORISTA" -> "Aguardando motorista";
            case "MOTORISTA_DESIGNADO" -> "Motorista designado";
            case "A_CAMINHO" -> "A caminho";
            case "ENTREGUE" -> "Entregue";
            case "NAO_ENTREGUE" -> "Não entregue";
            default -> etapa;
        };
    }

    static String tipoVolume(TipoVolume tipo) {
        return tipo == null ? "" : switch (tipo) {
            case MALA_RIGIDA -> "Mala rígida";
            case MALA_TECIDO -> "Mala de tecido";
            case MOCHILA -> "Mochila";
            case BOLSA -> "Bolsa";
            case CAIXA -> "Caixa";
            case INSTRUMENTO -> "Instrumento musical";
            case CARRINHO_BEBE -> "Carrinho de bebê";
            case OUTRO -> "Outro";
        };
    }

    static String tamanho(TamanhoBagagem tamanho) {
        return tamanho == null ? "" : switch (tamanho) {
            case P -> "P (até 55 cm)";
            case M -> "M (até 70 cm)";
            case G -> "G (acima de 70 cm)";
        };
    }

    static String cor(CorBagagem cor) {
        return cor == null ? "" : switch (cor) {
            case PRETA -> "Preta";
            case CINZA -> "Cinza";
            case BRANCA -> "Branca";
            case BEGE -> "Bege";
            case MARROM -> "Marrom";
            case VERMELHA -> "Vermelha";
            case LARANJA -> "Laranja";
            case AMARELA -> "Amarela";
            case VERDE -> "Verde";
            case AZUL -> "Azul";
            case ROXA -> "Roxa";
            case ROSA -> "Rosa";
        };
    }

    static String sinais(Collection<SinalParticular> sinais) {
        return sinais == null ? "" : sinais.stream().map(sinal -> switch (sinal) {
            case FITA -> "Fitas";
            case ADESIVO -> "Adesivos";
            case CADEADO -> "Cadeado";
            case AVARIA -> "Avarias";
        }).collect(Collectors.joining(", "));
    }

    static String origemLocalizacao(OrigemLocalizacao origem) {
        return origem == null ? "" : switch (origem) {
            case FOTO -> "GPS da foto";
            case APARELHO -> "GPS do celular";
            case NENHUMA -> "Sem localização";
        };
    }

    /** Data e hora de Brasilia, como o funcionario as viu. */
    static String dataHora(Instant instante) {
        return instante == null ? "" : DATA_HORA.format(instante);
    }

    static String distancia(Integer metros) {
        if (metros == null) {
            return "";
        }
        return metros < 1000 ? metros + " m" : String.format(java.util.Locale.of("pt", "BR"), "%.1f km", metros / 1000.0);
    }
}
