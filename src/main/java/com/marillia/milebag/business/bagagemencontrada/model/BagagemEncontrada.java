package com.marillia.milebag.business.bagagemencontrada.model;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.marillia.milebag.core.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Bagagem encontrada num aeroporto, registrada por um funcionario da companhia aerea (RD20): com etiqueta (HU34) ou sem
 * etiqueta, descrita pelo funcionario e identificada pelo codigo SID (HU35, RD21). Os valores lidos da foto
 * ({@code lido*}) ficam ao lado dos conferidos: a conferencia e o que vale. Ver V42 e V43.
 */
@Entity
@Table(name = "bagagem_encontrada")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BagagemEncontrada extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoBagagemEncontrada tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SituacaoBagagemEncontrada situacao;

    @Column(nullable = false, updatable = false)
    private String foto;

    @Column(name = "lido_numero_etiqueta", updatable = false)
    private String lidoNumeroEtiqueta;

    @Column(name = "lido_companhia", updatable = false)
    private String lidoCompanhia;

    @Column(name = "lido_destino", updatable = false)
    private String lidoDestino;

    @Column(name = "lido_voo", updatable = false)
    private String lidoVoo;

    @Column(name = "lido_nome_passageiro", updatable = false)
    private String lidoNomePassageiro;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_leitura", nullable = false, updatable = false)
    private OrigemLeitura origemLeitura;

    @Column(name = "texto_lido", updatable = false)
    private String textoLido;

    @Column(name = "numero_etiqueta")
    private String numeroEtiqueta;

    private String companhia;

    private String destino;

    private String voo;

    @Column(name = "nome_passageiro")
    private String nomePassageiro;

    private Double latitude;

    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_localizacao", nullable = false, updatable = false)
    private OrigemLocalizacao origemLocalizacao;

    @Column(name = "aeroporto_codigo")
    private String aeroportoCodigo;

    @Column(name = "distancia_aeroporto_m")
    private Integer distanciaAeroportoMetros;

    @Enumerated(EnumType.STRING)
    @Column(name = "lido_cor", updatable = false)
    private CorBagagem lidoCor;

    @Enumerated(EnumType.STRING)
    private CorBagagem cor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_volume")
    private TipoVolume tipoVolume;

    @Enumerated(EnumType.STRING)
    private TamanhoBagagem tamanho;

    @Convert(converter = SinaisParticularesConverter.class)
    private Set<SinalParticular> sinais = EnumSet.noneOf(SinalParticular.class);

    private String marca;

    private String observacoes;

    @Column(name = "codigo_sid")
    private String codigoSid;

    @Column(name = "criado_por", nullable = false, updatable = false)
    private UUID criadoPor;

    @Column(name = "registrada_em")
    private Instant registradaEm;

    /** Rascunho de bagagem com etiqueta: a foto ja lida, aguardando a conferencia. */
    public static BagagemEncontrada rascunhoComEtiqueta(
            UUID criadoPor, String foto, LeituraEtiqueta leitura, Double latitude, Double longitude, OrigemLocalizacao origemLocalizacao,
            String aeroportoSugerido, Integer distanciaMetros) {
        BagagemEncontrada bagagem = new BagagemEncontrada();
        bagagem.tipo = TipoBagagemEncontrada.COM_ETIQUETA;
        bagagem.situacao = SituacaoBagagemEncontrada.RASCUNHO;
        bagagem.criadoPor = criadoPor;
        bagagem.foto = foto;
        bagagem.lidoNumeroEtiqueta = leitura.numeroEtiqueta();
        bagagem.lidoCompanhia = leitura.companhia();
        bagagem.lidoDestino = leitura.destino();
        bagagem.lidoVoo = leitura.voo();
        bagagem.lidoNomePassageiro = leitura.nomePassageiro();
        bagagem.origemLeitura = leitura.origem();
        bagagem.textoLido = leitura.texto();
        bagagem.latitude = latitude;
        bagagem.longitude = longitude;
        bagagem.origemLocalizacao = origemLocalizacao;
        bagagem.aeroportoCodigo = aeroportoSugerido;
        bagagem.distanciaAeroportoMetros = distanciaMetros;
        return bagagem;
    }

    /** Rascunho de bagagem sem etiqueta (HU35): a foto, a cor sugerida por ela e o local, aguardando a descricao. */
    public static BagagemEncontrada rascunhoSemEtiqueta(
            UUID criadoPor, String foto, CorBagagem corSugerida, Double latitude, Double longitude, OrigemLocalizacao origemLocalizacao,
            String aeroportoSugerido, Integer distanciaMetros) {
        BagagemEncontrada bagagem = new BagagemEncontrada();
        bagagem.tipo = TipoBagagemEncontrada.SEM_ETIQUETA;
        bagagem.situacao = SituacaoBagagemEncontrada.RASCUNHO;
        bagagem.criadoPor = criadoPor;
        bagagem.foto = foto;
        bagagem.origemLeitura = OrigemLeitura.NENHUMA;
        bagagem.lidoCor = corSugerida;
        bagagem.latitude = latitude;
        bagagem.longitude = longitude;
        bagagem.origemLocalizacao = origemLocalizacao;
        bagagem.aeroportoCodigo = aeroportoSugerido;
        bagagem.distanciaAeroportoMetros = distanciaMetros;
        return bagagem;
    }

    /** A conferencia (RF27): os dados da etiqueta que valem e o aeroporto do registro. A distancia so vale para o sugerido. */
    public void registrarEtiqueta(
            String numeroEtiqueta, String companhia, String destino, String voo, String nomePassageiro, String aeroportoCodigo, Integer distancia,
            Instant quando) {
        this.numeroEtiqueta = numeroEtiqueta;
        this.companhia = companhia;
        this.destino = destino;
        this.voo = voo;
        this.nomePassageiro = nomePassageiro;
        this.aeroportoCodigo = aeroportoCodigo;
        this.distanciaAeroportoMetros = distancia;
        this.situacao = SituacaoBagagemEncontrada.REGISTRADA;
        this.registradaEm = quando;
    }

    /** A descricao (RF28) e o codigo SID (RD21), gerado com o aeroporto escolhido. */
    public void registrarDescricao(
            TipoVolume tipoVolume, TamanhoBagagem tamanho, CorBagagem cor, Set<SinalParticular> sinais, String marca, String observacoes,
            String aeroportoCodigo, Integer distancia, String codigoSid, Instant quando) {
        this.tipoVolume = tipoVolume;
        this.tamanho = tamanho;
        this.cor = cor;
        this.sinais = sinais.isEmpty() ? EnumSet.noneOf(SinalParticular.class) : EnumSet.copyOf(sinais);
        this.marca = marca;
        this.observacoes = observacoes;
        this.aeroportoCodigo = aeroportoCodigo;
        this.distanciaAeroportoMetros = distancia;
        this.codigoSid = codigoSid;
        this.situacao = SituacaoBagagemEncontrada.REGISTRADA;
        this.registradaEm = quando;
    }

    public boolean semEtiqueta() {
        return tipo == TipoBagagemEncontrada.SEM_ETIQUETA;
    }

    public boolean rascunho() {
        return situacao == SituacaoBagagemEncontrada.RASCUNHO;
    }

    public boolean temLocalizacao() {
        return latitude != null && longitude != null;
    }
}
