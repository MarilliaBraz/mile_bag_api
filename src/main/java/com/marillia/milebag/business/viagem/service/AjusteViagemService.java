package com.marillia.milebag.business.viagem.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.viagem.model.RegraCobranca;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.tenant.service.UsuarioConsultaService;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.tarifa.model.TabelaTarifa;
import com.marillia.milebag.business.tarifa.service.TabelaTarifaService;
import com.marillia.milebag.business.viagem.model.AjusteViagem;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoStatus;
import com.marillia.milebag.business.viagem.model.TrechoPedagio;
import com.marillia.milebag.business.viagem.repository.AjusteViagemRepository;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.AcessoNegadoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Registra e resolve correcoes de valores calculados de um {@link Trajeto}
 * ja consolidado (HU17) - ver contracts/relatorio-motorista-api.md. Fonte
 * unica de "resolucao do valor vigente" de um {@code (viagemId, campo)},
 * consumida tanto pelo relatorio do motorista ({@code
 * RelatorioMotoristaService}) quanto pelo fechamento ({@code
 * FechamentoService}, HU12, FR-006) - nenhum dos dois calcula {@code
 * TARIFA_APLICADA}/{@code VALOR_PEDAGIO} por conta propria.
 *
 * <p>{@link Optional#empty()} significa "sem ajuste E sem valor original
 * calculavel" (ex.: nenhuma tarifa aplicavel configurada; pedagio
 * sinalizado sem praca cadastrada vinculavel, HU10) - o chamador decide o
 * que fazer (ex.: {@code FechamentoService} marca o item PENDENTE, nunca
 * inventa um valor).</p>
 *
 * <p>Acessa {@link TrajetoRepository} diretamente (mesmo dominio,
 * business/viagem) - diferente de {@code BagagemCaronaService}/{@code
 * EntregaService}/{@code TabelaTarifaService}, de outros dominios, sempre
 * via Service (CONVENTIONS.md 2.1).</p>
 */
@Service
public class AjusteViagemService {

    private static final Logger log = LoggerFactory.getLogger(AjusteViagemService.class);

    private final AjusteViagemRepository ajusteViagemRepository;
    private final TrajetoRepository trajetoRepository;
    private final EntregaService entregaService;
    private final TabelaTarifaService tabelaTarifaService;
    private final BagagemCaronaService bagagemCaronaService;
    private final ObjectMapper objectMapper;
    private final BdoService bdoService;
    private final CobrancaViagemService cobrancaViagemService;
    private final UsuarioConsultaService usuarioConsultaService;

    public AjusteViagemService(
            AjusteViagemRepository ajusteViagemRepository,
            TrajetoRepository trajetoRepository,
            EntregaService entregaService,
            TabelaTarifaService tabelaTarifaService,
            BagagemCaronaService bagagemCaronaService,
            ObjectMapper objectMapper,
            BdoService bdoService,
            CobrancaViagemService cobrancaViagemService,
            UsuarioConsultaService usuarioConsultaService) {
        this.bdoService = bdoService;
        this.cobrancaViagemService = cobrancaViagemService;
        this.usuarioConsultaService = usuarioConsultaService;
        this.ajusteViagemRepository = ajusteViagemRepository;
        this.trajetoRepository = trajetoRepository;
        this.entregaService = entregaService;
        this.tabelaTarifaService = tabelaTarifaService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.objectMapper = objectMapper;
    }

    /**
     * O valor vigente do par (viagemId, campo): o {@code valorNovo} do
     * {@link AjusteViagem} mais recente, ou o valor final consolidado do
     * {@code Trajeto} se nenhum ajuste existir (data-model.md, "Resolucao
     * do valor vigente de um campo"). Vazio quando nao ha ajuste e o valor
     * original tambem nao e calculavel ainda.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> resolverValorVigente(UUID viagemId, CampoAjusteViagem campo) {
        return resolverValorVigente(buscarTrajetoOuFalhar(viagemId), campo);
    }

    @Transactional(readOnly = true)
    public List<AjusteViagem> listarPorViagem(UUID viagemId, UUID motoristaId) {
        Trajeto trajeto = buscarTrajetoOuFalhar(viagemId);
        validarPertenceAoMotorista(trajeto, motoristaId);
        List<AjusteViagem> ajustes = ajusteViagemRepository.findByViagemIdOrderByCriadoEmDesc(viagemId);
        // V58: com a tarifa oculta para este motorista, o historico tambem nao mostra as correcoes de tarifa.
        if (!usuarioConsultaService.ocultaTarifa(motoristaId)) {
            return ajustes;
        }
        return ajustes.stream().filter(ajuste -> ajuste.getCampo() != CampoAjusteViagem.TARIFA_APLICADA).toList();
    }

    /** V52: a regra de cobranca da viagem (cidade da base ou por km); vazia se a viagem nao existe. */
    @Transactional(readOnly = true)
    public Optional<RegraCobranca> regraCobranca(UUID viagemId) {
        return trajetoRepository.findById(viagemId).map(trajeto -> regraDaViagem(trajeto, entregaDoTrajeto(trajeto)));
    }

    /** Ponto de leitura publico para {@code RelatorioMotoristaService}. */
    @Transactional(readOnly = true)
    public boolean temAjuste(UUID viagemId) {
        return !ajusteViagemRepository.findByViagemIdOrderByCriadoEmDesc(viagemId).isEmpty();
    }

    /**
     * FR-004/FR-007/FR-008: justificativa obrigatoria, trajeto de uma
     * entrega do proprio motorista, e ja consolidado. {@code valorAnterior}
     * e sempre resolvido pelo servidor no momento da criacao (FR-009), nunca
     * recebido do cliente - se o valor vigente atual nao for calculavel
     * (campo TARIFA_APLICADA/VALOR_PEDAGIO sem base para calculo, ver classe),
     * a correcao e recusada: nao ha o que "corrigir" sem um valor de partida.
     */
    @Transactional
    public AjusteViagem registrarAjuste(UUID viagemId, UUID motoristaId, CampoAjusteViagem campo, String valorNovo, String justificativa) {
        if (justificativa == null || justificativa.isBlank()) {
            throw new RegraNegocioException("justificativa e obrigatoria para registrar um ajuste.");
        }
        if (campo == CampoAjusteViagem.TARIFA_APLICADA && usuarioConsultaService.ocultaTarifa(motoristaId)) {
            throw new RegraNegocioException("A empresa nao mostra a tarifa a este motorista; a correcao de tarifa e feita pela empresa.");
        }
        BigDecimal valorNovoDecimal = parseValor(valorNovo);

        Trajeto trajeto = buscarTrajetoOuFalhar(viagemId);
        validarPertenceAoMotorista(trajeto, motoristaId);
        if (trajeto.getStatus() != TrajetoStatus.ENCERRADO) {
            throw new RecursoNaoEncontradoException("Esta entrega ainda nao tem trajeto consolidado; nao ha valor calculado a corrigir.");
        }

        // Item 9: no km, o valor anterior e o que estava sendo pago (o percorrido, quando ele passou o calculado).
        Optional<BigDecimal> vigente = campo == CampoAjusteViagem.KM_IDA ? kmPagos(trajeto).map(KmPagos::ida)
                : campo == CampoAjusteViagem.KM_VOLTA ? kmPagos(trajeto).map(KmPagos::volta)
                : resolverValorVigente(trajeto, campo);
        BigDecimal valorAnterior = vigente
                .orElseThrow(() -> new RegraNegocioException(
                        "Nao ha valor calculado de " + campo + " para corrigir nesta entrega (ver limitacao conhecida em research.md)."));

        AjusteViagem ajuste = ajusteViagemRepository.save(
                new AjusteViagem(viagemId, campo, valorAnterior.toPlainString(), valorNovoDecimal.toPlainString(), justificativa, motoristaId));

        log.info("Ajuste {} ({}) registrado no trajeto {} pelo motorista {}.", ajuste.getId(), campo, viagemId, motoristaId);
        return ajuste;
    }

    private Optional<BigDecimal> resolverValorVigente(Trajeto trajeto, CampoAjusteViagem campo) {
        return ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(trajeto.getId(), campo)
                .map(ajuste -> new BigDecimal(ajuste.getValorNovo()))
                .map(Optional::of)
                .orElseGet(() -> valorOriginal(trajeto, campo));
    }

    private Optional<BigDecimal> valorOriginal(Trajeto trajeto, CampoAjusteViagem campo) {
        return switch (campo) {
            case KM_IDA -> Optional.ofNullable(trajeto.getKmIdaFinal());
            case KM_VOLTA -> Optional.ofNullable(trajeto.getKmVoltaFinal());
            case TARIFA_APLICADA -> calcularTarifaAplicadaOriginal(trajeto);
            case VALOR_PEDAGIO -> calcularValorPedagioOriginal(trajeto);
        };
    }

    /**
     * V52 (item 7): entrega na cidade da base paga so o valor fixo da tarifa; fora dela, so km x valor por km (ida + volta
     * a partir do aeroporto + km complementar das caronas). A regra vem congelada na viagem; nas anteriores a V52 e
     * decidida aqui, pelo BDO. Sem o valor que a regra pede, fica pendente (vazio), como uma tarifa nao configurada.
     */
    private Optional<BigDecimal> calcularTarifaAplicadaOriginal(Trajeto trajeto) {
        Entrega entrega = entregaDoTrajeto(trajeto);
        Optional<TabelaTarifa> tarifa = tabelaTarifaService.resolverTarifaAplicavel(motoristaDoTrajeto(trajeto, entrega), null);
        if (tarifa.isEmpty()) {
            return Optional.empty();
        }
        RegraCobranca regra = regraDaViagem(trajeto, entrega);
        if (regra == null) {
            // Nao deu para saber se a entrega e na cidade da base (servico de mapa fora): pendente, nunca um palpite.
            return Optional.empty();
        }
        if (regra == RegraCobranca.FIXO_CIDADE_BASE) {
            return Optional.ofNullable(tarifa.get().getValorFixo());
        }
        Optional<KmPagos> km = kmPagos(trajeto);
        if (km.isEmpty() || tarifa.get().getValorPorKm() == null) {
            return Optional.empty();
        }
        BigDecimal kmComplementar = bagagemCaronaService.listarPorEntrega(entrega.getId()).stream()
                .map(BagagemCarona::getQuilometragemComplementar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal kmTotal = km.get().ida().add(km.get().volta()).add(kmComplementar);
        return Optional.of(tarifa.get().getValorPorKm().multiply(kmTotal));
    }

    /** Item 9: o km percorrido so substitui o calculado quando passa a ida calculada em mais que isto (oscilacao do GPS). */
    static final BigDecimal TOLERANCIA_KM_PERCORRIDO = new BigDecimal("1.05");

    /** Km da ida e da volta que entram no pagamento por km, e se vieram do caminho percorrido (item 9). */
    public record KmPagos(BigDecimal ida, BigDecimal volta, boolean usouPercorrido) {
    }

    /**
     * Item 9: na viagem paga por km, se a ida percorrida (GPS) passar a ida calculada em mais de 5%, paga a percorrida
     * na ida e na volta (a volta espelha a ida, item 7). Um ajuste manual do motorista vale acima de tudo, como antes;
     * percorrido menor que o calculado nunca reduz o pagamento.
     */
    private Optional<KmPagos> kmPagos(Trajeto trajeto) {
        Optional<BigDecimal> kmIda = resolverValorVigente(trajeto, CampoAjusteViagem.KM_IDA);
        Optional<BigDecimal> kmVolta = resolverValorVigente(trajeto, CampoAjusteViagem.KM_VOLTA);
        if (kmIda.isEmpty() || kmVolta.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal percorrido = trajeto.getKmPercorrido();
        boolean idaAjustada = temAjuste(trajeto, CampoAjusteViagem.KM_IDA);
        boolean usaPercorrido = !idaAjustada && percorrido != null && trajeto.getKmIdaFinal() != null
                && percorrido.compareTo(trajeto.getKmIdaFinal().multiply(TOLERANCIA_KM_PERCORRIDO)) > 0;
        if (!usaPercorrido) {
            return Optional.of(new KmPagos(kmIda.get(), kmVolta.get(), false));
        }
        BigDecimal volta = temAjuste(trajeto, CampoAjusteViagem.KM_VOLTA) ? kmVolta.get() : percorrido;
        return Optional.of(new KmPagos(percorrido, volta, true));
    }

    /** Item 9: os km pagos da viagem, para a auditoria da empresa (vazio se ainda nao ha km consolidado). */
    @Transactional(readOnly = true)
    public Optional<KmPagos> kmPagos(UUID viagemId) {
        return trajetoRepository.findById(viagemId).flatMap(this::kmPagos);
    }

    private boolean temAjuste(Trajeto trajeto, CampoAjusteViagem campo) {
        return ajusteViagemRepository.findFirstByViagemIdAndCampoOrderByCriadoEmDesc(trajeto.getId(), campo).isPresent();
    }

    private RegraCobranca regraDaViagem(Trajeto trajeto, Entrega entrega) {
        if (trajeto.getRegraCobranca() != null) {
            return trajeto.getRegraCobranca();
        }
        Optional<com.marillia.milebag.business.bdo.model.Bdo> bdo = bdoService.buscarPorId(entrega.getBdoId());
        return bdo.isEmpty() ? RegraCobranca.POR_KM : cobrancaViagemService.cobrancaDe(bdo.get()).regra();
    }

    /**
     * {@code Trajeto.pracasPedagioFinal} guarda um trecho por praca cadastrada no tracado (HU24, com o valor
     * por passagem congelado) ou, quando o motor de rotas so sinaliza pedagio, um trecho generico sem praca
     * (HU04). Sem trecho, o pedagio original e zero.
     */
    private Optional<BigDecimal> calcularValorPedagioOriginal(Trajeto trajeto) {
        List<TrechoPedagio> trechos = lerPracasPedagio(trajeto.getPracasPedagioFinal());
        if (trechos == null || trechos.isEmpty()) {
            return Optional.of(BigDecimal.ZERO);
        }
        // HU24: so calcula se TODA praca do trajeto tem valor congelado; um trecho generico (pedagio sem praca
        // cadastrada) ou uma praca sem valor vigente deixa o item pendente em vez de inventar um numero.
        boolean todosValorados = trechos.stream().allMatch(trecho -> trecho.valorCadastrado() && trecho.valorUnitario() != null);
        if (!todosValorados) {
            return Optional.empty();
        }
        // RN06: reembolso de pedagio sempre ida e volta (2x o valor por passagem).
        return Optional.of(trechos.stream().map(TrechoPedagio::valorUnitario).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(BigDecimal.valueOf(2)));
    }

    private List<TrechoPedagio> lerPracasPedagio(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<TrechoPedagio>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler as pracas de pedagio do trajeto.", e);
        }
    }

    private Entrega entregaDoTrajeto(Trajeto trajeto) {
        return entregaService.buscarPorId(trajeto.getEntregaId())
                .orElseThrow(() -> new IllegalStateException("Trajeto " + trajeto.getId() + " sem entrega associada."));
    }

    /**
     * Quem dirigiu (V37). Viagens anteriores a V37 sem esse dado caem no motorista responsavel da entrega, como
     * antes; a de uma tentativa sem sucesso continua sendo de quem a fez, mesmo com a entrega ja reaberta.
     */
    private UUID motoristaDoTrajeto(Trajeto trajeto, Entrega entrega) {
        return trajeto.getMotoristaId() != null ? trajeto.getMotoristaId() : entrega.getMotoristaResponsavel();
    }

    private void validarPertenceAoMotorista(Trajeto trajeto, UUID motoristaId) {
        if (!motoristaId.equals(motoristaDoTrajeto(trajeto, entregaDoTrajeto(trajeto)))) {
            throw new AcessoNegadoException("Este trajeto nao pertence a uma entrega do motorista autenticado.");
        }
    }

    private Trajeto buscarTrajetoOuFalhar(UUID viagemId) {
        return trajetoRepository.findById(viagemId).orElseThrow(() -> new RecursoNaoEncontradoException("Trajeto (viagem) nao encontrado."));
    }

    private BigDecimal parseValor(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new RegraNegocioException("valorNovo e obrigatorio para registrar um ajuste.");
        }
        try {
            return new BigDecimal(valor);
        } catch (NumberFormatException e) {
            throw new RegraNegocioException("valorNovo deve ser um numero valido.");
        }
    }
}
