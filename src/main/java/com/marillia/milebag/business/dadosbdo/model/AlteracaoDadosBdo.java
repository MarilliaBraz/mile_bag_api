package com.marillia.milebag.business.dadosbdo.model;

import java.time.Instant;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A empresa corrigiu os dados do passageiro de um BDO (V59), com motivo - historico com o antes e o depois de cada
 * campo; nunca editada. Campo que nao mudou fica com o anterior igual ao novo.
 */
@Entity
@Table(name = "alteracao_dados_bdo")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlteracaoDadosBdo extends BaseEntity {

    @Column(name = "bdo_id", nullable = false, updatable = false)
    private UUID bdoId;

    @Column(name = "nome_anterior", updatable = false)
    private String nomeAnterior;

    @Column(name = "nome_novo", updatable = false)
    private String nomeNovo;

    @Column(name = "endereco_anterior", updatable = false)
    private String enderecoAnterior;

    @Column(name = "endereco_novo", updatable = false)
    private String enderecoNovo;

    @Column(name = "telefone_anterior", updatable = false)
    private String telefoneAnterior;

    @Column(name = "telefone_novo", updatable = false)
    private String telefoneNovo;

    @Column(name = "email_anterior", updatable = false)
    private String emailAnterior;

    @Column(name = "email_novo", updatable = false)
    private String emailNovo;

    @Column(nullable = false, updatable = false, columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "alterado_por", nullable = false, updatable = false)
    private UUID alteradoPor;

    @Column(name = "data_hora", nullable = false, updatable = false)
    private Instant dataHora;

    public AlteracaoDadosBdo(UUID bdoId, ValoresPassageiro anterior, ValoresPassageiro novo, String motivo, UUID alteradoPor,
            Instant dataHora) {
        this.bdoId = bdoId;
        this.nomeAnterior = anterior.nome();
        this.nomeNovo = novo.nome();
        this.enderecoAnterior = anterior.endereco();
        this.enderecoNovo = novo.endereco();
        this.telefoneAnterior = anterior.telefone();
        this.telefoneNovo = novo.telefone();
        this.emailAnterior = anterior.email();
        this.emailNovo = novo.email();
        this.motivo = motivo;
        this.alteradoPor = alteradoPor;
        this.dataHora = dataHora;
    }

    public ValoresPassageiro anterior() {
        return new ValoresPassageiro(nomeAnterior, enderecoAnterior, telefoneAnterior, emailAnterior);
    }

    public ValoresPassageiro novo() {
        return new ValoresPassageiro(nomeNovo, enderecoNovo, telefoneNovo, emailNovo);
    }
}
