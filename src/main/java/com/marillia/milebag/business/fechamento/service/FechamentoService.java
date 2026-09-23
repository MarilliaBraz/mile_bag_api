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

import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.model.EntregaStatus;
import com.marillia.milebag.business.entrega.service.EntregaService;
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
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.service.AjusteViagemService;
import com.marillia.milebag.business.viagem.service.ViagemService;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RecursoNaoEncontradoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Gera e consulta fechamentos periodicos (US1 desta HU) - ver
 * contracts/fechamento-api.md. Le o trajeto consolidado via {@code
 * ViagemService}, mas nunca calcula quilometragem/tarifa/pedagio por conta
 * propria - {@code AjusteViagemService} (business/viagem) e a fonte unica
 * dessa resolucao desde HU17 (FR-006: usa o valor vigente, ajustado pelo
 * motorista quando houver, nunca sempre o bruto do Trajeto).
 */
@Service
public class FechamentoService {

    private static final Logger log = LoggerFactory.getLogger(FechamentoService.class);

    private final FechamentoRepository fechamentoRepository;
    private final ItemFechamentoRepository itemFechamentoRepository;
    private final ComprovanteEntregaService comprovanteEntregaService;
    private final EntregaService entregaService;
    private final ViagemService viagemService;
    private final AjusteViagemService ajusteViagemService;

    public FechamentoService(
            FechamentoRepository fechamentoRepository,
            ItemFechamentoRepository itemFechamentoRepository,
            ComprovanteEntregaService comprovanteEntregaService,
            EntregaService entregaService,
            ViagemService viagemService,
            AjusteViagemService ajusteViagemService) {
        this.fechamentoRepository = fechamentoRepository;
        this.itemFechamentoRepository = itemFechamentoRepository;
        this.comprovanteEntregaService = comprovanteEntregaService;
        this.entregaService = entregaService;
        this.viagemService = viagemService;
        this.ajusteViagemService = ajusteViagemService;
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

        return comprovanteEntregaService.listarPorPeriodo(inicioInstant, fimInstant).stream()
                .map(ComprovanteEntrega::getEntregaId)
                .map(entregaService::buscarPorId)
                .flatMap(Optional::stream)
                .filter(entrega -> entrega.getStatus() == EntregaStatus.ENCERRADA_COM_SUCESSO)
                .toList();
    }

    /**
     * FR-006 (HU12) + HU17 FR-006: os tres valores vem de {@code
     * AjusteViagemService.resolverValorVigente} - ja ajustado pelo motorista
     * quando houver um {@code AjusteViagem} mais recente, senao o valor
     * original calculado (tarifa/pedagio). Uma falha de resolucao (tarifa
     * nao configurada, ou pedagio sinalizado sem praca cadastrada
     * vinculavel - ver javadoc de {@code AjusteViagemService}) marca so
     * aquela entrega como PENDENTE, sem interromper as demais.
     */
    private ItemFechamento calcularItem(UUID fechamentoId, Entrega entrega) {
        UUID motoristaId = entrega.getMotoristaResponsavel();

        Trajeto trajeto = viagemService.buscarTrajetoPorEntrega(entrega.getId()).orElse(null);
        if (trajeto == null || trajeto.getKmIdaFinal() == null || trajeto.getKmVoltaFinal() == null) {
            return new ItemFechamento(
                    fechamentoId, entrega.getId(), motoristaId, null, null, StatusItemFechamento.PENDENTE, "trajeto da viagem nao consolidado");
        }

        List<String> problemas = new ArrayList<>();
        Optional<BigDecimal> valorPagamento = ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA);
        if (valorPagamento.isEmpty()) {
            problemas.add("nenhuma tarifa aplicavel configurada para o motorista/regiao");
        }
        Optional<BigDecimal> valorPedagio = ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO);
        if (valorPedagio.isEmpty()) {
            problemas.add("pedagio identificado no trajeto sem praca de pedagio cadastrada vinculavel (ver HU10)");
        }

        if (!problemas.isEmpty()) {
            return new ItemFechamento(
                    fechamentoId, entrega.getId(), motoristaId, null, null, StatusItemFechamento.PENDENTE, String.join("; ", problemas));
        }
        return new ItemFechamento(
                fechamentoId, entrega.getId(), motoristaId, valorPagamento.get(), valorPedagio.get(), StatusItemFechamento.CALCULADO, null);
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

    public record GeracaoResultado(FechamentoResponse resumo, boolean novo) {
    }
}
