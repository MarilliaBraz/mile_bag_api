package com.marillia.milebag.business.bdo.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Regra de completude dos dados de contato do BDO (RN15, HU28). Avaliada
 * sobre o proprio BDO, nao so na conferencia do OCR, porque o BDO recebido
 * por e-mail (RF21) nao passa pela conferencia. O app do motorista repete a
 * mesma regra na conferencia, para o aviso acompanhar a digitacao
 * ({@code captura-bdo/completudeContato.ts}) - as duas precisam mudar juntas.
 */
public final class CompletudeContatoBdo {

    /** "S/N" (ou "SN") conta como numero informado: e como o endereco sem numero e escrito na pratica. */
    private static final Pattern NUMERO_NO_ENDERECO = Pattern.compile("\\d|\\bs\\s*/?\\s*n\\b", Pattern.CASE_INSENSITIVE);

    /** DDD + numero (10 ou 11 digitos), com ou sem o DDI 55 na frente (12 ou 13). */
    private static final int MINIMO_DIGITOS_TELEFONE = 10;
    private static final int MAXIMO_DIGITOS_TELEFONE = 13;

    private CompletudeContatoBdo() {
    }

    public static List<PendenciaContatoBdo> avaliar(String enderecoEntrega, String telefonePassageiro) {
        List<PendenciaContatoBdo> pendencias = new ArrayList<>();
        if (enderecoEntrega == null || enderecoEntrega.isBlank()) {
            pendencias.add(PendenciaContatoBdo.ENDERECO_AUSENTE);
        } else if (!NUMERO_NO_ENDERECO.matcher(enderecoEntrega).find()) {
            pendencias.add(PendenciaContatoBdo.ENDERECO_SEM_NUMERO);
        }
        if (telefonePassageiro == null || telefonePassageiro.isBlank()) {
            pendencias.add(PendenciaContatoBdo.TELEFONE_AUSENTE);
        } else {
            int digitos = telefonePassageiro.replaceAll("\\D", "").length();
            if (digitos < MINIMO_DIGITOS_TELEFONE || digitos > MAXIMO_DIGITOS_TELEFONE) {
                pendencias.add(PendenciaContatoBdo.TELEFONE_INVALIDO);
            }
        }
        return List.copyOf(pendencias);
    }
}
