package com.marillia.milebag.business.bdo.mail;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.marillia.milebag.business.bdo.model.CamposExtraidosBdo;
import com.marillia.milebag.business.bdo.service.CamposBdoExtractor;

/**
 * Extrai os campos do telex WorldTracer a partir do corpo de texto do
 * e-mail (FR-005) - sem OCR, diferente de HU01, ja que o conteudo aqui ja
 * chega como texto estruturado (research.md desta HU, secao 3).
 *
 * <p>Mesma ressalva de {@code BdoOcrService}: nenhum layout exato do telex
 * foi fornecido nas evidencias do projeto, entao a extracao por rotulo de
 * linha e uma heuristica de melhor esforco. Aqui, porem, sem tela de
 * conferencia para corrigir depois (RN01/RI01 nao se aplicam a esta via),
 * um e-mail sem todos os campos obrigatorios NAO gera BDO (FR-007,
 * data-model.md) - os mesmos tres campos minimos exigidos tambem na
 * confirmacao de HU01 ({@code BdoService}, T014): numero, passageiro e
 * endereco de entrega.</p>
 */
@Component
public class BdoTelexParser {

    public Optional<CamposExtraidosBdo> parsear(String corpoEmail) {
        CamposExtraidosBdo campos = CamposBdoExtractor.extrair(corpoEmail);

        if (campoAusente(campos.numero()) || campoAusente(campos.passageiroNome()) || campoAusente(campos.enderecoEntrega())) {
            return Optional.empty();
        }
        return Optional.of(campos);
    }

    private boolean campoAusente(String valor) {
        return valor == null || valor.isBlank();
    }
}
