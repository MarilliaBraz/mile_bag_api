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

    public AjusteViagemService(
            AjusteViagemRepository ajusteViagemRepository,
            TrajetoRepository trajetoRepository,
            EntregaService entregaService,
            TabelaTarifaService tabelaTarifaService,
            BagagemCaronaService bagagemCaronaService,
            ObjectMapper objectMapper) {
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
        return ajusteViagemRepository.findByViagemIdOrderByCriadoEmDesc(viagemId);
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
        BigDecimal valorNovoDecimal = parseValor(valorNovo);

        Trajeto trajeto = buscarTrajetoOuFalhar(viagemId);
        validarPertenceAoMotorista(trajeto, motoristaId);
        if (trajeto.getStatus() != TrajetoStatus.ENCERRADO) {
            throw new RecursoNaoEncontradoException("Esta entrega ainda nao tem trajeto consolidado; nao ha valor calculado a corrigir.");
        }

        BigDecimal valorAnterior = resolverValorVigente(trajeto, campo)
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

    /** Mesma formula ja usada por {@code FechamentoService}, com o km vigente (considerando ajuste de KM_IDA/KM_VOLTA, se houver). */
    private Optional<BigDecimal> calcularTarifaAplicadaOriginal(Trajeto trajeto) {
        Entrega entrega = entregaDoTrajeto(trajeto);
        Optional<BigDecimal> kmIda = resolverValorVigente(trajeto, CampoAjusteViagem.KM_IDA);
        Optional<BigDecimal> kmVolta = resolverValorVigente(trajeto, CampoAjusteViagem.KM_VOLTA);
        if (kmIda.isEmpty() || kmVolta.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal kmComplementar = bagagemCaronaService.listarPorEntrega(entrega.getId()).stream()
                .map(BagagemCarona::getQuilometragemComplementar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal kmTotal = kmIda.get().add(kmVolta.get()).add(kmComplementar);

        return tabelaTarifaService.resolverTarifaAplicavel(motoristaDoTrajeto(trajeto, entrega), null).map(tarifa -> valorPagamento(tarifa, kmTotal));
    }

    private BigDecimal valorPagamento(TabelaTarifa tarifa, BigDecimal kmTotal) {
        BigDecimal fixo = tarifa.getValorFixo() != null ? tarifa.getValorFixo() : BigDecimal.ZERO;
        BigDecimal porKm = tarifa.getValorPorKm() != null ? tarifa.getValorPorKm() : BigDecimal.ZERO;
        return fixo.add(porKm.multiply(kmTotal));
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
