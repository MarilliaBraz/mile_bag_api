package com.marillia.milebag.business.dadosbdo.model;

import com.marillia.milebag.business.bdo.model.DadosPassageiroBdo;

/** Corpo da correcao dos dados do passageiro (V59): os dados como ficam e o motivo (obrigatorio). */
public record AlterarDadosBdoRequest(DadosPassageiroBdo dados, String motivo) {
}
