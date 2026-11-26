package com.marillia.milebag.business.painelgestao.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.marillia.milebag.business.painelgestao.model.ConfigPainelGestao;
import com.marillia.milebag.business.painelgestao.model.MalaAberta;
import com.marillia.milebag.business.painelgestao.model.MalaEntregue;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoConfig;
import com.marillia.milebag.business.painelgestao.model.PainelGestaoResponse;
import com.marillia.milebag.business.painelgestao.repository.PainelGestaoConfigRepository;
import com.marillia.milebag.business.painelgestao.repository.PainelGestaoRepository;
import com.marillia.milebag.core.exception.RegraNegocioException;

/** Painel de gestao da empresa de entrega (fase 1) e a sua configuracao (V61). */
@Service
public class PainelGestaoService {

    static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    static final int PRAZO_PADRAO_HORAS = 24;
    static final int HORAS_SEM_ATUALIZACAO_PADRAO = 4;
    static final int PRAZO_MAXIMO_HORAS = 720;
    static final int HORAS_SEM_ATUALIZACAO_MAXIMO = 168;
    static final int DIAS_HISTORICO = 30;

    private final PainelGestaoRepository painelRepository;
    private final PainelGestaoConfigRepository configRepository;

    public PainelGestaoService(PainelGestaoRepository painelRepository, PainelGestaoConfigRepository configRepository) {
        this.painelRepository = painelRepository;
        this.configRepository = configRepository;
    }

    @Transactional(readOnly = true)
    public PainelGestaoResponse painel() {
        ConfigPainelGestao config = configuracao();
        Instant agora = Instant.now();
        Instant inicioHoje = LocalDate.now(BRASILIA).atStartOfDay(BRASILIA).toInstant();
        List<MalaAberta> abertas = painelRepository.malasAbertas(config.prazoEntregaHoras()).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::aberta).toList();
        List<MalaEntregue> entregues = painelRepository
                .malasEntregues(config.prazoEntregaHoras(), inicioHoje.minus(Duration.ofDays(DIAS_HISTORICO))).stream()
                .map(LinhaNativa::new).map(PainelGestaoService::entregue).toList();
        return CalculoPainelGestao.calcular(abertas, entregues, agora, inicioHoje, config.prazoEntregaHoras(), config.horasSemAtualizacao());
    }

    @Transactional(readOnly = true)
    public ConfigPainelGestao configuracao() {
        return configRepository.findFirstByOrderByCriadoEmAsc()
                .map(c -> new ConfigPainelGestao(c.getPrazoEntregaHoras(), c.getHorasSemAtualizacao()))
                .orElse(new ConfigPainelGestao(PRAZO_PADRAO_HORAS, HORAS_SEM_ATUALIZACAO_PADRAO));
    }

    @Transactional
    public ConfigPainelGestao salvar(ConfigPainelGestao pedido) {
        int prazo = validar(pedido == null ? null : pedido.prazoEntregaHoras(), PRAZO_MAXIMO_HORAS,
                "O prazo padrao deve ficar entre 1 e " + PRAZO_MAXIMO_HORAS + " horas.");
        int parada = validar(pedido == null ? null : pedido.horasSemAtualizacao(), HORAS_SEM_ATUALIZACAO_MAXIMO,
                "As horas sem atualizacao devem ficar entre 1 e " + HORAS_SEM_ATUALIZACAO_MAXIMO + ".");
        PainelGestaoConfig config = configRepository.findFirstByOrderByCriadoEmAsc().orElseGet(() -> new PainelGestaoConfig(prazo, parada));
        config.atualizar(prazo, parada);
        configRepository.save(config);
        return new ConfigPainelGestao(prazo, parada);
    }

    private static int validar(Integer valor, int maximo, String mensagem) {
        if (valor == null || valor < 1 || valor > maximo) {
            throw new RegraNegocioException(mensagem);
        }
        return valor;
    }

    private static MalaAberta aberta(LinhaNativa l) {
        return new MalaAberta(l.uuid(0), l.uuid(1), l.texto(2), l.texto(3), l.texto(4), l.texto(5), l.texto(6), l.instante(7),
                l.instante(8), l.instante(9), l.numero(10), l.texto(11), l.logico(12));
    }

    private static MalaEntregue entregue(LinhaNativa l) {
        return new MalaEntregue(l.uuid(0), l.uuid(1), l.texto(2), l.texto(3), l.texto(4), l.texto(5), l.instante(6), l.instante(7),
                l.instante(8), l.logico(9), l.logico(10), l.logico(11), l.logico(12), l.logico(13));
    }
}
