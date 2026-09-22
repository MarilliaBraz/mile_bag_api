package com.marillia.milebag.business.fechamento.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.repository.ComprovanteEntregaRepository;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCarona;
import com.marillia.milebag.business.entrega.bagagemcarona.BagagemCaronaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.repository.EntregaRepository;
import com.marillia.milebag.business.fechamento.model.Fechamento;
import com.marillia.milebag.business.fechamento.model.FechamentoDetalheResponse;
import com.marillia.milebag.business.fechamento.model.FechamentoResponse;
import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.model.ItemFechamentoResponse;
import com.marillia.milebag.business.fechamento.model.StatusFechamento;
import com.marillia.milebag.business.fechamento.model.StatusItemFechamento;
import com.marillia.milebag.business.fechamento.model.TotalPorMotorista;
import com.marillia.milebag.business.fechamento.repository.FechamentoRepository;
import com.marillia.milebag.business.fechamento.repository.ItemFechamentoRepository;
import com.marillia.milebag.business.tarifa.model.TabelaTarifa;
import com.marillia.milebag.business.tarifa.service.TabelaTarifaService;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrechoPedagio;
import com.marillia.milebag.business.viagem.repository.TrajetoRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Gera e consulta fechamentos periodicos (US1 desta HU) - ver
 * contracts/fechamento-api.md. Orquestra business/tarifa e le
 * diretamente os campos finais de business/viagem (Trajeto), nunca
 * recalculando a partir de uma nova consulta a mapas (RN02).
 */
@Service
public class FechamentoService {

    private static final Logger log = LoggerFactory.getLogger(FechamentoService.class);

    private final FechamentoRepository fechamentoRepository;
    private final ItemFechamentoRepository itemFechamentoRepository;
    private final ComprovanteEntregaRepository comprovanteEntregaRepository;
    private final EntregaRepository entregaRepository;
    private final TrajetoRepository trajetoRepository;
    private final TabelaTarifaService tabelaTarifaService;
    private final BagagemCaronaService bagagemCaronaService;
    private final ObjectMapper objectMapper;

    public FechamentoService(
            FechamentoRepository fechamentoRepository,
            ItemFechamentoRepository itemFechamentoRepository,
            ComprovanteEntregaRepository comprovanteEntregaRepository,
            EntregaRepository entregaRepository,
            TrajetoRepository trajetoRepository,
            TabelaTarifaService tabelaTarifaService,
            BagagemCaronaService bagagemCaronaService,
            ObjectMapper objectMapper) {
        this.fechamentoRepository = fechamentoRepository;
        this.itemFechamentoRepository = itemFechamentoRepository;
        this.comprovanteEntregaRepository = comprovanteEntregaRepository;
        this.entregaRepository = entregaRepository;
        this.trajetoRepository = trajetoRepository;
        this.tabelaTarifaService = tabelaTarifaService;
        this.bagagemCaronaService = bagagemCaronaService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public GeracaoResultado gerar(LocalDate periodoInicio, LocalDate periodoFim) {
        if (periodoInicio == null || periodoFim == null) {
            throw new RegraNegocioException("periodoInicio e periodoFim sao obrigatorios.");
        }
        if (periodoFim.isBefore(periodoInicio)) {
            throw new RegraNegocioException("periodoFim deve ser posterior ou igual a periodoInicio.");
        }

        Optional<Fechamento> existente = fechamentoRepository.findByPeriodoInicioAndPeriodoFim(periodoInicio, periodoFim);
        if (existente.isPresent()) {
            log.info("Fechamento ja existente para {} a {} - reaproveitado (idempotencia, FR-005).", periodoInicio, periodoFim);
            return new GeracaoResultado(montarResumo(existente.get()), false);
        }
        verificarSobreposicao(periodoInicio, periodoFim);

        Fechamento fechamento = fechamentoRepository.save(new Fechamento(periodoInicio, periodoFim));

        List<Entrega> entregasElegiveis = buscarEntregasElegiveis(periodoInicio, periodoFim);
        boolean algumPendente = false;
        for (Entrega entrega : entregasElegiveis) {
            ItemFechamento item = itemFechamentoRepository.save(calcularItem(fechamento.getId(), entrega));
            if (item.getStatusItem() == StatusItemFechamento.PENDENTE) {
                algumPendente = true;
            }
        }

        fechamento.atualizarStatus(algumPendente ? StatusFechamento.COM_PENDENCIAS : StatusFechamento.COMPLETO);
        fechamento = fechamentoRepository.save(fechamento);

        log.info(
                "Fechamento {} gerado para {} a {} ({} entrega(s), status {})",
                fechamento.getId(), periodoInicio, periodoFim, entregasElegiveis.size(), fechamento.getStatus());
        return new GeracaoResultado(montarResumo(fechamento), true);
    }

    @Transactional(readOnly = true)
    public List<FechamentoResponse> listar(LocalDate de, LocalDate ate) {
        return fechamentoRepository.findAll().stream()
                .filter(f -> de == null || !f.getPeriodoFim().isBefore(de))
                .filter(f -> ate == null || !f.getPeriodoInicio().isAfter(ate))
                .map(this::montarResumo)
                .toList();
    }

    @Transactional(readOnly = true)
    public FechamentoDetalheResponse detalhar(UUID id) {
        Fechamento fechamento = buscarOuFalhar(id);
        List<ItemFechamento> itens = itemFechamentoRepository.findByFechamentoId(id);
        return new FechamentoDetalheResponse(
                fechamento.getId(),
                fechamento.getPeriodoInicio(),
                fechamento.getPeriodoFim(),
                fechamento.getGeradoEm(),
                fechamento.getStatus(),
                itens.stream().map(ItemFechamentoResponse::de).toList());
    }

    Fechamento buscarOuFalhar(UUID id) {
        return fechamentoRepository.findById(id).orElseThrow(() -> new RecursoNaoEncontradoException("Fechamento nao encontrado."));
    }

    List<ItemFechamento> itensDe(UUID fechamentoId) {
        return itemFechamentoRepository.findByFechamentoId(fechamentoId);
    }

    /** FR-004: so entregas encerradas com comprovante cuja data/hora cai dentro do periodo. */
    private List<Entrega> buscarEntregasElegiveis(LocalDate periodoInicio, LocalDate periodoFim) {
        Instant inicioInstant = periodoInicio.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fimInstant = periodoFim.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        return comprovanteEntregaRepository.findByDataHoraBetween(inicioInstant, fimInstant).stream()
                .map(ComprovanteEntrega::getEntregaId)
                .map(entregaRepository::findById)
                .flatMap(Optional::stream)
                .filter(entrega -> entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO)
                .toList();
    }

    /**
     * FR-006: uma falha de resolucao (tarifa ou pedagio) marca so aquela
     * entrega como PENDENTE, sem interromper as demais.
     *
     * <p><b>Limitacao conhecida</b>: {@code Trajeto.pracasPedagioFinal}
     * (HU04) guarda so um sinalizador textual de pedagio, sem valor
     * monetario nem referencia a uma {@code PracaPedagio} especifica (foi
     * construido antes de HU10 existir) - por isso, quando ha pedagio
     * sinalizado, este item fica PENDENTE em vez de usar um valor
     * inventado ou o de uma praca qualquer do cadastro do tenant.</p>
     */
    private ItemFechamento calcularItem(UUID fechamentoId, Entrega entrega) {
        List<String> problemas = new ArrayList<>();
        UUID motoristaId = entrega.getMotoristaResponsavel();

        Trajeto trajeto = trajetoRepository.findByEntregaId(entrega.getId()).orElse(null);
        if (trajeto == null || trajeto.getKmIdaFinal() == null || trajeto.getKmVoltaFinal() == null) {
            problemas.add("trajeto da viagem nao consolidado");
        }

        BigDecimal valorPagamento = null;
        if (problemas.isEmpty()) {
            BigDecimal kmTotal = trajeto.getKmIdaFinal().add(trajeto.getKmVoltaFinal()).add(kmComplementarBagagemCarona(entrega.getId()));

            Optional<TabelaTarifa> tarifa = tabelaTarifaService.resolverTarifaAplicavel(motoristaId, null);
            if (tarifa.isEmpty()) {
                problemas.add("nenhuma tarifa aplicavel configurada para o motorista/regiao");
            } else {
                BigDecimal fixo = tarifa.get().getValorFixo() != null ? tarifa.get().getValorFixo() : BigDecimal.ZERO;
                BigDecimal porKm = tarifa.get().getValorPorKm() != null ? tarifa.get().getValorPorKm() : BigDecimal.ZERO;
                valorPagamento = fixo.add(porKm.multiply(kmTotal));
            }
        }

        BigDecimal valorPedagio = null;
        if (problemas.isEmpty()) {
            List<TrechoPedagio> trechos = lerPracasPedagio(trajeto.getPracasPedagioFinal());
            if (trechos != null && !trechos.isEmpty()) {
                problemas.add("pedagio identificado no trajeto sem praca de pedagio cadastrada vinculavel (ver HU10)");
            } else {
                valorPedagio = BigDecimal.ZERO;
            }
        }

        if (!problemas.isEmpty()) {
            return new ItemFechamento(
                    fechamentoId, entrega.getId(), motoristaId, null, null, StatusItemFechamento.PENDENTE, String.join("; ", problemas));
        }
        return new ItemFechamento(
                fechamentoId, entrega.getId(), motoristaId, valorPagamento, valorPedagio, StatusItemFechamento.CALCULADO, null);
    }

    /** Edge Case do spec.md desta HU: bagagem carona entra na entrega principal, nunca como linha separada. */
    private BigDecimal kmComplementarBagagemCarona(UUID entregaId) {
        return bagagemCaronaService.listarPorEntrega(entregaId).stream()
                .map(BagagemCarona::getQuilometragemComplementar)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void verificarSobreposicao(LocalDate inicio, LocalDate fim) {
        boolean sobrepoe = fechamentoRepository.findAll().stream()
                .filter(f -> !(f.getPeriodoInicio().equals(inicio) && f.getPeriodoFim().equals(fim)))
                .anyMatch(f -> !f.getPeriodoFim().isBefore(inicio) && !f.getPeriodoInicio().isAfter(fim));
        if (sobrepoe) {
            throw new ConflitoException("O periodo informado se sobrepoe parcialmente a um fechamento ja existente.");
        }
    }

    private FechamentoResponse montarResumo(Fechamento fechamento) {
        List<ItemFechamento> itens = itemFechamentoRepository.findByFechamentoId(fechamento.getId());
        Map<UUID, BigDecimal[]> totais = new LinkedHashMap<>();
        for (ItemFechamento item : itens) {
            if (item.getStatusItem() != StatusItemFechamento.CALCULADO) {
                continue;
            }
            BigDecimal[] valores = totais.computeIfAbsent(item.getMotoristaId(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            valores[0] = valores[0].add(item.getValorPagamentoEntrega());
            valores[1] = valores[1].add(item.getValorReembolsoPedagio());
        }

        List<TotalPorMotorista> totaisPorMotorista =
                totais.entrySet().stream().map(e -> new TotalPorMotorista(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();

        return new FechamentoResponse(
                fechamento.getId(), fechamento.getPeriodoInicio(), fechamento.getPeriodoFim(), fechamento.getGeradoEm(), fechamento.getStatus(),
                totaisPorMotorista);
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

    public record GeracaoResultado(FechamentoResponse resumo, boolean novo) {
    }
}
