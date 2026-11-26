package com.marillia.milebag.business.pagamento.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.fechamento.model.ItemFechamento;
import com.marillia.milebag.business.fechamento.service.FechamentoService;
import com.marillia.milebag.business.pagamento.model.EventoPagamentoMotorista;
import com.marillia.milebag.business.pagamento.model.EventoPagamentoResponse;
import com.marillia.milebag.business.pagamento.model.PagamentoMotorista;
import com.marillia.milebag.business.pagamento.model.PagamentoMotoristaResponse;
import com.marillia.milebag.business.pagamento.model.SituacaoPagamento;
import com.marillia.milebag.business.pagamento.model.SituacaoPagamentoMotorista;
import com.marillia.milebag.business.pagamento.model.TipoEventoPagamento;
import com.marillia.milebag.business.pagamento.repository.EventoPagamentoMotoristaRepository;
import com.marillia.milebag.business.pagamento.repository.PagamentoMotoristaRepository;
import com.marillia.milebag.core.exception.ConflitoException;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Pagamento da quinzena de cada motorista num fechamento (V60): a empresa agenda a data e marca como pago conforme for
 * pagando; o pago marcado por engano se desfaz com motivo. Cada passo fica no rastro (quem e quando). A entrega paga
 * sai da tela de Entregas e so volta pela busca do numero do BDO.
 */
@Service
public class PagamentoMotoristaService {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final int MOTIVO_MAX = 500;

    private final PagamentoMotoristaRepository pagamentoRepository;
    private final EventoPagamentoMotoristaRepository eventoRepository;
    private final FechamentoService fechamentoService;

    public PagamentoMotoristaService(PagamentoMotoristaRepository pagamentoRepository, EventoPagamentoMotoristaRepository eventoRepository,
            FechamentoService fechamentoService) {
        this.pagamentoRepository = pagamentoRepository;
        this.eventoRepository = eventoRepository;
        this.fechamentoService = fechamentoService;
    }

    /** Um pagamento por motorista que tem entrega calculada no fechamento; quem nao tem nada calculado nao entra. */
    @Transactional(readOnly = true)
    public List<PagamentoMotoristaResponse> listar(UUID fechamentoId) {
        Map<UUID, List<ItemFechamento>> porMotorista = fechamentoService.itensCalculados(fechamentoId).stream()
                .collect(Collectors.groupingBy(ItemFechamento::getMotoristaId, LinkedHashMap::new, Collectors.toList()));
        Map<UUID, PagamentoMotorista> pagamentos = pagamentoRepository.findByFechamentoId(fechamentoId).stream()
                .collect(Collectors.toMap(PagamentoMotorista::getMotoristaId, Function.identity()));
        Map<UUID, List<EventoPagamentoResponse>> eventos = eventosPorPagamento(pagamentos.values());
        return porMotorista.entrySet().stream()
                .map(grupo -> resposta(grupo.getKey(), grupo.getValue(), pagamentos.get(grupo.getKey()), eventos))
                .toList();
    }

    @Transactional
    public void agendar(UUID fechamentoId, UUID motoristaId, LocalDate data, UUID usuarioId) {
        if (data == null) {
            throw new RegraNegocioException("Informe a data do pagamento.");
        }
        if (data.isBefore(hoje())) {
            throw new RegraNegocioException("A data do pagamento agendado nao pode ser no passado.");
        }
        PagamentoMotorista pagamento = obterOuCriar(fechamentoId, motoristaId, usuarioId);
        exigirNaoPago(pagamento, "Este pagamento ja foi marcado como pago. Desfaca o pago antes de reagendar.");
        pagamento.agendar(data, usuarioId);
        registrar(pagamentoRepository.save(pagamento), TipoEventoPagamento.AGENDADO, data, null, usuarioId);
    }

    /** Sem data, vale o dia de hoje (Brasilia). */
    @Transactional
    public void marcarPago(UUID fechamentoId, UUID motoristaId, LocalDate data, UUID usuarioId) {
        LocalDate pagoEm = data == null ? hoje() : data;
        if (pagoEm.isAfter(hoje())) {
            throw new RegraNegocioException("A data do pagamento nao pode ser no futuro.");
        }
        PagamentoMotorista pagamento = obterOuCriar(fechamentoId, motoristaId, usuarioId);
        exigirNaoPago(pagamento, "Este pagamento ja esta marcado como pago.");
        pagamento.marcarPago(pagoEm, usuarioId);
        registrar(pagamentoRepository.save(pagamento), TipoEventoPagamento.PAGO, pagoEm, null, usuarioId);
    }

    /** Pago marcado por engano: volta para agendado (ou a agendar) e as entregas voltam para a tela. Motivo obrigatorio. */
    @Transactional
    public void desfazerPago(UUID fechamentoId, UUID motoristaId, String motivo, UUID usuarioId) {
        String motivoLimpo = motivo == null ? "" : motivo.strip();
        if (motivoLimpo.isEmpty()) {
            throw new RegraNegocioException("Informe o motivo para desfazer o pagamento.");
        }
        if (motivoLimpo.length() > MOTIVO_MAX) {
            throw new RegraNegocioException("O motivo pode ter ate " + MOTIVO_MAX + " caracteres.");
        }
        PagamentoMotorista pagamento = pagamentoRepository.findByFechamentoIdAndMotoristaId(fechamentoId, motoristaId)
                .filter(existente -> existente.getSituacao() == SituacaoPagamento.PAGO)
                .orElseThrow(() -> new ConflitoException("Este pagamento nao esta marcado como pago."));
        LocalDate pagoEm = pagamento.getPagoEm();
        pagamento.desfazerPago(usuarioId);
        registrar(pagamentoRepository.save(pagamento), TipoEventoPagamento.PAGAMENTO_DESFEITO, pagoEm, motivoLimpo, usuarioId);
    }

    /** Das entregas informadas, as ja pagas ao motorista, com a data do pagamento (para sair da tela de Entregas). */
    @Transactional(readOnly = true)
    public Map<UUID, LocalDate> pagasEntre(Collection<UUID> entregaIds) {
        List<ItemFechamento> itens = fechamentoService.itensCalculadosDasEntregas(entregaIds);
        if (itens.isEmpty()) {
            return Map.of();
        }
        Map<String, LocalDate> pagos = pagamentoRepository.findBySituacao(SituacaoPagamento.PAGO).stream()
                .collect(Collectors.toMap(p -> chave(p.getFechamentoId(), p.getMotoristaId()), PagamentoMotorista::getPagoEm));
        return itens.stream()
                .filter(item -> pagos.containsKey(chave(item.getFechamentoId(), item.getMotoristaId())))
                .collect(Collectors.toMap(ItemFechamento::getEntregaId, item -> pagos.get(chave(item.getFechamentoId(), item.getMotoristaId())),
                        (primeira, outra) -> primeira));
    }

    /** O pagamento da quinzena para o relatorio do motorista; vazio se a empresa ainda nao fechou exatamente este periodo. */
    @Transactional(readOnly = true)
    public Optional<SituacaoPagamentoMotorista> situacaoParaMotorista(UUID motoristaId, LocalDate periodoInicio, LocalDate periodoFim) {
        return fechamentoService.buscarPorPeriodo(periodoInicio, periodoFim)
                .map(fechamento -> pagamentoRepository.findByFechamentoIdAndMotoristaId(fechamento.getId(), motoristaId)
                        .map(p -> new SituacaoPagamentoMotorista(p.getSituacao(), p.getDataAgendada(), p.getPagoEm()))
                        .orElse(new SituacaoPagamentoMotorista(SituacaoPagamento.A_AGENDAR, null, null)));
    }

    private PagamentoMotorista obterOuCriar(UUID fechamentoId, UUID motoristaId, UUID usuarioId) {
        boolean temEntregaCalculada = fechamentoService.itensCalculados(fechamentoId).stream()
                .anyMatch(item -> item.getMotoristaId().equals(motoristaId));
        if (!temEntregaCalculada) {
            throw new RegraNegocioException("Este motorista nao tem entrega calculada neste fechamento.");
        }
        return pagamentoRepository.findByFechamentoIdAndMotoristaId(fechamentoId, motoristaId)
                .orElseGet(() -> new PagamentoMotorista(fechamentoId, motoristaId, usuarioId));
    }

    private static void exigirNaoPago(PagamentoMotorista pagamento, String mensagem) {
        if (pagamento.getSituacao() == SituacaoPagamento.PAGO) {
            throw new ConflitoException(mensagem);
        }
    }

    private void registrar(PagamentoMotorista pagamento, TipoEventoPagamento tipo, LocalDate data, String motivo, UUID usuarioId) {
        eventoRepository.save(new EventoPagamentoMotorista(pagamento.getId(), tipo, data, motivo, usuarioId, Instant.now()));
    }

    private Map<UUID, List<EventoPagamentoResponse>> eventosPorPagamento(Collection<PagamentoMotorista> pagamentos) {
        if (pagamentos.isEmpty()) {
            return Map.of();
        }
        return eventoRepository.findByPagamentoMotoristaIdInOrderByDataHoraAsc(pagamentos.stream().map(PagamentoMotorista::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(EventoPagamentoMotorista::getPagamentoMotoristaId,
                        Collectors.mapping(EventoPagamentoResponse::de, Collectors.toList())));
    }

    private static PagamentoMotoristaResponse resposta(UUID motoristaId, List<ItemFechamento> itens, PagamentoMotorista pagamento,
            Map<UUID, List<EventoPagamentoResponse>> eventos) {
        BigDecimal entregas = somar(itens, ItemFechamento::getValorPagamentoEntrega);
        BigDecimal pedagios = somar(itens, ItemFechamento::getValorReembolsoPedagio);
        if (pagamento == null) {
            return new PagamentoMotoristaResponse(motoristaId, entregas, pedagios, SituacaoPagamento.A_AGENDAR, null, null, List.of());
        }
        return new PagamentoMotoristaResponse(motoristaId, entregas, pedagios, pagamento.getSituacao(), pagamento.getDataAgendada(),
                pagamento.getPagoEm(), eventos.getOrDefault(pagamento.getId(), List.of()));
    }

    private static BigDecimal somar(List<ItemFechamento> itens, Function<ItemFechamento, BigDecimal> valor) {
        return itens.stream().map(valor).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String chave(UUID fechamentoId, UUID motoristaId) {
        return fechamentoId + "/" + motoristaId;
    }

    LocalDate hoje() {
        return LocalDate.now(BRASILIA);
    }
}
