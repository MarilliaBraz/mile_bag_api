package com.marillia.milebag.business.bdo.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cobre RN15/HU28: o que conta como endereco e telefone incompletos. */
class CompletudeContatoBdoTest {

    @Test
    void enderecoComNumeroETelefoneComDddNaoTemPendencia() {
        assertThat(CompletudeContatoBdo.avaliar("Rua das Flores, 120 - Centro", "(62) 99876-5432")).isEmpty();
    }

    @Test
    void telefoneComDdiTambemEValido() {
        assertThat(CompletudeContatoBdo.avaliar("Rua A, 1", "+55 62 99876-5432")).isEmpty();
    }

    @Test
    void enderecoSemNumeroESinalizado() {
        assertThat(CompletudeContatoBdo.avaliar("Rua das Flores - Centro", "62998765432"))
                .containsExactly(PendenciaContatoBdo.ENDERECO_SEM_NUMERO);
    }

    @Test
    void enderecoComSnContaComoNumeroInformado() {
        assertThat(CompletudeContatoBdo.avaliar("Rodovia GO-020, S/N, Km 5", "62998765432")).isEmpty();
        assertThat(CompletudeContatoBdo.avaliar("Chacara Boa Vista sn", "62998765432")).isEmpty();
    }

    @Test
    void camposAusentesOuEmBrancoSaoSinalizados() {
        assertThat(CompletudeContatoBdo.avaliar(null, "  "))
                .containsExactly(PendenciaContatoBdo.ENDERECO_AUSENTE, PendenciaContatoBdo.TELEFONE_AUSENTE);
    }

    @Test
    void telefoneSemDddOuComDigitosDemaisEInvalido() {
        assertThat(CompletudeContatoBdo.avaliar("Rua A, 1", "99876-5432")).containsExactly(PendenciaContatoBdo.TELEFONE_INVALIDO);
        assertThat(CompletudeContatoBdo.avaliar("Rua A, 1", "0055629987654321")).containsExactly(PendenciaContatoBdo.TELEFONE_INVALIDO);
    }
}
