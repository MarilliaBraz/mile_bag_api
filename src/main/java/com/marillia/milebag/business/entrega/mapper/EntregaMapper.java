package com.marillia.milebag.business.entrega.mapper;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaBdoResumo;
import com.marillia.milebag.business.entrega.model.EntregaResponse;

@Component
public class EntregaMapper {

    public EntregaResponse toResponse(Entrega entrega, Bdo bdo) {
        EntregaBdoResumo resumo = new EntregaBdoResumo(
                bdo.getOrigem(),
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getEtiquetaBagagem());

        return new EntregaResponse(
                entrega.getId(),
                entrega.getBdoId(),
                entrega.getStatus(),
                entrega.getMotoristaResponsavel(),
                resumo);
    }
}
