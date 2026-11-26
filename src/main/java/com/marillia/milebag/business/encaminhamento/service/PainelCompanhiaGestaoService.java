package com.marillia.milebag.business.encaminhamento.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.encaminhamento.model.EtapaEntrega;
import com.marillia.milebag.business.encaminhamento.model.PainelCompanhiaGestaoResponse;
import com.marillia.milebag.business.encaminhamento.model.TipoVoo;
import com.marillia.milebag.business.encaminhamento.repository.PainelCompanhiaRepository;
import com.marillia.milebag.core.base.LinhaNativa;

/**
 * Painel de gestao da companhia aerea (fase 1): prazo da ANAC de cada processo em aberto (abertura do PIR + 7 dias no
 * voo domestico ou 21 no internacional), tempos dos ultimos 30 dias, cadeia de custodia e aeroportos.
 */
@Service
public class PainelCompanhiaGestaoService {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final int DIAS_HISTORICO = 30;

    private final PainelCompanhiaRepository repository;

    public PainelCompanhiaGestaoService(PainelCompanhiaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PainelCompanhiaGestaoResponse painel(UUID companhiaId) {
        Instant agora = Instant.now();
        Instant inicioHoje = LocalDate.now(BRASILIA).atStartOfDay(BRASILIA).toInstant();
        List<CalculoPainelCompanhia.Encaminhamento> encaminhamentos = repository
                .encaminhamentos(companhiaId, inicioHoje.minus(Duration.ofDays(DIAS_HISTORICO))).stream()
                .map(LinhaNativa::new)
                .map(PainelCompanhiaGestaoService::encaminhamento)
                .toList();
        List<CalculoPainelCompanhia.NaSala> naSala = repository.naSala(companhiaId).stream()
                .map(LinhaNativa::new)
                .map(l -> new CalculoPainelCompanhia.NaSala(l.uuid(0), l.texto(1), l.instante(2), l.texto(3), l.texto(4)))
                .toList();
        return CalculoPainelCompanhia.calcular(encaminhamentos, naSala, agora, inicioHoje);
    }

    private static CalculoPainelCompanhia.Encaminhamento encaminhamento(LinhaNativa l) {
        Instant abertoEm = l.instante(5);
        TipoVoo tipoVoo = TipoVoo.valueOf(l.texto(4));
        return new CalculoPainelCompanhia.Encaminhamento(l.uuid(0), l.texto(1), l.texto(2), EtapaEntrega.valueOf(l.texto(3)), abertoEm,
                abertoEm.plus(Duration.ofDays(tipoVoo.diasPrazoAnac())), l.instante(6), l.instante(8), l.instante(9), l.texto(10),
                l.instante(11), l.texto(12));
    }
}
