package com.marillia.milebag.business.bdo.mail;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.service.LeitorTelexBdo;

/**
 * Reconhece o telex WorldTracer no corpo do e-mail ({@link LeitorTelexBdo}, V49). Sem tela de conferencia nesta via,
 * um e-mail sem os campos minimos (numero, passageiro e endereco de entrega) NAO gera BDO (FR-007) - fica para a
 * empresa incluir pelo painel.
 */
@Component
public class BdoTelexParser {

    private final LeitorTelexBdo leitorTelexBdo;

    public BdoTelexParser(LeitorTelexBdo leitorTelexBdo) {
        this.leitorTelexBdo = leitorTelexBdo;
    }

    public Optional<CamposExtraidosBdo> parsear(String corpoEmail) {
        // Um BDO real tem menos de 2 mil caracteres; o resto do e-mail (assinaturas, historico) nao interessa.
        String corpo = corpoEmail != null && corpoEmail.length() > LeitorTelexBdo.TAMANHO_MAXIMO_TEXTO
                ? corpoEmail.substring(0, LeitorTelexBdo.TAMANHO_MAXIMO_TEXTO) : corpoEmail;
        CamposExtraidosBdo campos = leitorTelexBdo.ler(corpo);

        if (campoAusente(campos.numero()) || campoAusente(campos.passageiroNome()) || campoAusente(campos.enderecoEntrega())) {
            return Optional.empty();
        }
        return Optional.of(campos);
    }

    private boolean campoAusente(String valor) {
        return valor == null || valor.isBlank();
    }
}
