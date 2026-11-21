package com.marillia.milebag.business.bdo.mapper;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.model.BdoResponse;
import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;

@Component
public class BdoMapper {

    public BdoResponse toResponse(Bdo bdo) {
        CamposExtraidosBdo campos = new CamposExtraidosBdo(
                bdo.getNumero(),
                bdo.getPassageiroNome(),
                bdo.getEnderecoEntrega(),
                bdo.getCompanhiaAerea(),
                bdo.getTelefonePassageiro(),
                bdo.getEmailPassageiro(),
                bdo.getEtiquetaBagagem(),
                bdo.getTipoProcesso(),
                bdo.getCidade(),
                bdo.getUf(),
                bdo.getCep(),
                bdo.getInstrucoesEntrega(),
                bdo.getAeroportoRetirada(),
                bdo.getVooChegada(),
                bdo.getDataEntregaPrevista(),
                bdo.getQuantidadeMalas(),
                bdo.getCoresBagagem(),
                bdo.getValorInformado());

        return new BdoResponse(bdo.getId(), bdo.getOrigem(), campos, bdo.getDataCaptura(), bdo.pendenciasDeContato());
    }
}
