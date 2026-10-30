package com.marillia.milebag.business.encaminhamento.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bagagem encontrada encaminhada para uma empresa de entrega (HU37, RF30). Liga a bagagem (da companhia) ao BDO (da
 * empresa), por isso fica sem RLS e nao estende {@code BaseEntity}: o servico filtra sempre pela companhia do token, e
 * a {@code etapa} e escrita pelos triggers da V45 quando a entrega anda.
 */
@Entity
@Table(name = "encaminhamento_entrega")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncaminhamentoEntrega {

    @Id
    private UUID id;

    @Column(name = "bagagem_id", nullable = false, updatable = false)
    private UUID bagagemId;

    @Column(name = "companhia_id", nullable = false, updatable = false)
    private UUID companhiaId;

    @Column(name = "empresa_id", nullable = false, updatable = false)
    private UUID empresaId;

    @Column(name = "bdo_id")
    private UUID bdoId;

    @Column(name = "numero_processo", nullable = false, length = 30)
    private String numeroProcesso;

    @Column(name = "passageiro_nome", nullable = false, length = 200)
    private String passageiroNome;

    @Column(nullable = false, length = 300)
    private String endereco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EtapaEntrega etapa;

    @Column(name = "encaminhado_por", nullable = false, updatable = false)
    private UUID encaminhadoPor;

    @Column(name = "encaminhado_em", nullable = false, updatable = false)
    private Instant encaminhadoEm;

    @Column(name = "cancelado_por")
    private UUID canceladoPor;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public EncaminhamentoEntrega(
            UUID bagagemId, UUID companhiaId, UUID empresaId, UUID bdoId, String numeroProcesso, String passageiroNome, String endereco,
            UUID encaminhadoPor, Instant agora) {
        this.id = UUID.randomUUID();
        this.bagagemId = bagagemId;
        this.companhiaId = companhiaId;
        this.empresaId = empresaId;
        this.bdoId = bdoId;
        this.numeroProcesso = numeroProcesso;
        this.passageiroNome = passageiroNome;
        this.endereco = endereco;
        this.etapa = EtapaEntrega.AGUARDANDO_MOTORISTA;
        this.encaminhadoPor = encaminhadoPor;
        this.encaminhadoEm = agora;
        this.atualizadoEm = agora;
    }

    /** O BDO ja foi apagado da empresa; a chave estrangeira tambem o anularia. */
    public void cancelar(UUID funcionarioId, Instant agora) {
        this.etapa = EtapaEntrega.CANCELADO;
        this.bdoId = null;
        this.canceladoPor = funcionarioId;
        this.canceladoEm = agora;
        this.atualizadoEm = agora;
    }
}
