package com.marillia.milebag.business.bdo.model;

import java.time.Instant;
import java.util.List;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BDO (Baggage Delivery Order) - documento de origem de uma entrega.
 * Entidade compartilhada com o fluxo de RF21 (HU02); esta HU (HU01) so cria
 * BDOs com {@code origem = FOTO_MANUAL}.
 *
 * <p>Ver {@code data-model.md} desta HU e {@code requisitos/04-regras-dominio.md}
 * (RD02).</p>
 */
@Entity
@Table(name = "bdo")
@Getter
@Setter
@NoArgsConstructor
public class Bdo extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BdoOrigem origem;

    /**
     * Nulo ate a conferencia obrigatoria caso o OCR nao reconheca o campo na
     * captura; exigido apenas para confirmar (validado em BdoService).
     */
    @Column(length = 30)
    private String numero;

    @Column(name = "passageiro_nome", length = 200)
    private String passageiroNome;

    @Column(name = "endereco_entrega", length = 300)
    private String enderecoEntrega;

    @Column(name = "companhia_aerea", length = 100)
    private String companhiaAerea;

    @Column(name = "telefone_passageiro", length = 30)
    private String telefonePassageiro;

    @Column(name = "email_passageiro", length = 200)
    private String emailPassageiro;

    @Column(name = "etiqueta_bagagem", length = 50)
    private String etiquetaBagagem;

    /**
     * Referencia (caminho relativo) para a imagem original, gravada por
     * {@code ArmazenamentoImagemService} em volume com prefixo por tenant.
     * Obrigatoria apenas para {@code origem = FOTO_MANUAL} (FR-006) -
     * imutavel apos a criacao do registro (data-model.md).
     */
    @Column(name = "imagem_original", length = 500)
    private String imagemOriginal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_conferencia", nullable = false, length = 20)
    private StatusConferencia statusConferencia = StatusConferencia.PENDENTE;

    @Column(name = "data_captura", nullable = false)
    private Instant dataCaptura;

    /** RN15/HU28: so sinaliza - nunca usado para bloquear um fluxo. */
    public List<PendenciaContatoBdo> pendenciasDeContato() {
        return CompletudeContatoBdo.avaliar(enderecoEntrega, telefonePassageiro);
    }
}
