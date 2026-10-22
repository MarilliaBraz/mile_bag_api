package com.marillia.milebag.business.bdo.mapper;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;

@Component
public class BdoMapper {

    public BdoResponse toResponse(Bdo bdo) {
        CamposExtraidosBdo camposExtraidos = new CamposExtraidosBdo(
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getCompanhiaAerea(),
                bdo.getTelefonePassageiro(),
                bdo.getEmailPassageiro(),
                bdo.getEtiquetaBagagem());

        return new BdoResponse(
                bdo.getId(),
                bdo.getOrigem(),
                camposExtraidos,
                bdo.getImagemOriginal(),
                bdo.getStatusConferencia(),
                bdo.getDataCaptura(),
                bdo.pendenciasDeContato());
    }
}
