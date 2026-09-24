package com.marillia.milebag.business.viagem.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.bdo.model.Bdo;
import com.marillia.milebag.business.bdo.service.BdoService;
import com.marillia.milebag.business.comprovante.model.ComprovanteEntrega;
import com.marillia.milebag.business.comprovante.service.ComprovanteEntregaService;
import com.marillia.milebag.business.entrega.model.Entrega;
import com.marillia.milebag.business.entrega.service.EntregaService;
import com.marillia.milebag.business.viagem.model.CampoAjusteViagem;
import com.marillia.milebag.business.viagem.model.ItemRelatorioMotoristaResponse;
import com.marillia.milebag.business.viagem.model.RelatorioQuinzenalResponse;
import com.marillia.milebag.business.viagem.model.Trajeto;
import com.marillia.milebag.business.viagem.model.TrajetoStatus;
import com.marillia.milebag.core.exception.RegraNegocioException;

/**
 * Relatorio sob demanda do motorista (HU17, US1) - ver
 * contracts/relatorio-motorista-api.md. Usa a mesma base de entregas
 * "elegiveis" de {@code FechamentoService} (comprovante dentro do periodo),
 * restrita ao motorista autenticado e a entregas com trajeto ja consolidado
 * (FR-001, Edge Case 2) - nao persiste nada, apenas le.
 */
@Service
public class RelatorioMotoristaService {

    private final ComprovanteEntregaService comprovanteEntregaService;
    private final EntregaService entregaService;
    private final ViagemService viagemService;
    private final AjusteViagemService ajusteViagemService;
    private final BdoService bdoService;

    public RelatorioMotoristaService(
            ComprovanteEntregaService comprovanteEntregaService,
            EntregaService entregaService,
            ViagemService viagemService,
            AjusteViagemService ajusteViagemService,
            BdoService bdoService) {
        this.comprovanteEntregaService = comprovanteEntregaService;
        this.entregaService = entregaService;
        this.viagemService = viagemService;
        this.ajusteViagemService = ajusteViagemService;
        this.bdoService = bdoService;
    }

    @Transactional(readOnly = true)
    public RelatorioQuinzenalResponse consultarRelatorio(UUID motoristaId, LocalDate periodoInicio, LocalDate periodoFim) {
        if (periodoInicio == null || periodoFim == null) {
            throw new RegraNegocioException("periodoInicio e periodoFim sao obrigatorios.");
        }
        if (periodoFim.isBefore(periodoInicio)) {
            throw new RegraNegocioException("periodoFim deve ser posterior ou igual a periodoInicio.");
        }

        Instant inicioInstant = periodoInicio.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant fimInstant = periodoFim.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<ItemRelatorioMotoristaResponse> itens = comprovanteEntregaService.listarPorPeriodo(inicioInstant, fimInstant).stream()
                .map(ComprovanteEntrega::getEntregaId)
                .map(entregaService::buscarPorId)
                .flatMap(Optional::stream)
                .filter(entrega -> motoristaId.equals(entrega.getMotoristaResponsavel()))
                .map(this::montarItem)
                .flatMap(Optional::stream)
                .toList();

        return new RelatorioQuinzenalResponse(periodoInicio, periodoFim, itens);
    }

    private Optional<ItemRelatorioMotoristaResponse> montarItem(Entrega entrega) {
        Trajeto trajeto = viagemService.buscarTrajetoPorEntrega(entrega.getId()).orElse(null);
        if (trajeto == null || trajeto.getStatus() != TrajetoStatus.ENCERRADO) {
            return Optional.empty();
        }

        String bdoNumero = bdoService.buscarPorId(entrega.getBdoId()).map(Bdo::getNumero).orElse(null);
        // TARIFA_APLICADA/VALOR_PEDAGIO podem vir vazios quando nao ha base
        // de calculo ainda (ver AjusteViagemService) - null no relatorio
        // pessoal do motorista, nunca um zero inventado.
        return Optional.of(new ItemRelatorioMotoristaResponse(
                entrega.getId(),
                trajeto.getId(),
                bdoNumero,
                ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.KM_IDA).orElse(null),
                ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.KM_VOLTA).orElse(null),
                ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.TARIFA_APLICADA).orElse(null),
                ajusteViagemService.resolverValorVigente(trajeto.getId(), CampoAjusteViagem.VALOR_PEDAGIO).orElse(null),
                ajusteViagemService.temAjuste(trajeto.getId())));
    }
}
