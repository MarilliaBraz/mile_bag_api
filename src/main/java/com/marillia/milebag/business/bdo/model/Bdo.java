package com.marillia.milebag.business.bdo.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
 * Entra pelo e-mail (telex WorldTracer), pela companhia aerea parceira ou pela empresa (V49); a captura por foto
 * do motorista (FOTO_MANUAL) deixou de existir e so aparece nos BDOs antigos.
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

    @Column(name = "endereco_entrega", length = 500)
    private String enderecoEntrega;

    @Column(name = "companhia_aerea", length = 100)
    private String companhiaAerea;

    @Column(name = "telefone_passageiro", length = 30)
    private String telefonePassageiro;

    @Column(name = "email_passageiro", length = 200)
    private String emailPassageiro;

    /** Uma ou mais etiquetas (TN01, TN02...), separadas por virgula. */
    @Column(name = "etiqueta_bagagem", length = 300)
    private String etiquetaBagagem;

    /** AHL (mala extraviada), DPR (mala danificada)... - o tipo do processo no cabecalho do telex. */
    @Column(name = "tipo_processo", length = 10)
    private String tipoProcesso;

    @Column(length = 100)
    private String cidade;

    @Column(length = 2)
    private String uf;

    @Column(length = 9)
    private String cep;

    /** LD01, LD02, SI: complemento, horario combinado, voo que traz a mala. */
    @Column(name = "instrucoes_entrega", columnDefinition = "TEXT")
    private String instrucoesEntrega;

    /** Aeroporto onde o motorista retira a mala (ex.: GYN). */
    @Column(name = "aeroporto_retirada", length = 3)
    private String aeroportoRetirada;

    @Column(name = "voo_chegada", length = 20)
    private String vooChegada;

    @Column(name = "data_entrega_prevista")
    private LocalDate dataEntregaPrevista;

    @Column(name = "quantidade_malas")
    private Integer quantidadeMalas;

    /** CT01, CT02: cor e tipo de cada mala no codigo IATA (ex.: BK22), separados por virgula. */
    @Column(name = "cores_bagagem", length = 200)
    private String coresBagagem;

    /** .CS do telex (ex.: "BRL 132") - so informativo, nao entra no calculo do frete. */
    @Column(name = "valor_informado", length = 30)
    private String valorInformado;

    /** O telex como chegou (e-mail ou colado pela empresa), para conferencia. */
    @Column(name = "texto_original", columnDefinition = "TEXT")
    private String textoOriginal;

    /** Administrador que incluiu o BDO pelo painel (origem EMPRESA). */
    @Column(name = "incluido_por")
    private UUID incluidoPor;

    /**
     * Referencia (caminho relativo) para a imagem original, gravada por
     * {@code ArmazenamentoImagemService} em volume com prefixo por tenant.
     * So existe nos BDOs antigos de {@code origem = FOTO_MANUAL} (FR-006).
     */
    @Column(name = "imagem_original", length = 500)
    private String imagemOriginal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_conferencia", nullable = false, length = 20)
    private StatusConferencia statusConferencia = StatusConferencia.PENDENTE;

    @Column(name = "data_captura", nullable = false)
    private Instant dataCaptura;

    /** Motorista que fotografou - so nos BDOs antigos de {@code FOTO_MANUAL}. */
    @Column(name = "capturado_por")
    private UUID capturadoPor;

    /** RN15/HU28: so sinaliza - nunca usado para bloquear um fluxo. */
    public List<PendenciaContatoBdo> pendenciasDeContato() {
        return CompletudeContatoBdo.avaliar(enderecoEntrega, telefonePassageiro);
    }
}
